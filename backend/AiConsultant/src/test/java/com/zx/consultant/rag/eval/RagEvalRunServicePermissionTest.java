package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.rag.eval.entity.RagEvalRun;
import com.zx.consultant.rag.eval.vo.RagEvalRunDetailVO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 详情不能只凭记录 ID 返回。创建、成功、失败的事务必须独立提交。
 */
class RagEvalRunServicePermissionTest {

    @Test
    void detailRequiresOwner() {
        StubRunService service = new StubRunService(ownedRun(9L));

        RagEvalRunDetailVO detail = service.getDetail(9L, 7L);

        assertEquals(9L, detail.getId());
        assertEquals("q", detail.getCases().get(0).getQuery());
        assertEquals(1, detail.getResult().getTotal());
        assertEquals(5, detail.getConfigSnapshot().get("evalTopK"));
    }

    @Test
    void detailRejectsOtherUserMissingRecordAndAnonymous() {
        StubRunService service = new StubRunService(ownedRun(9L));

        BaseException otherUser = assertThrows(BaseException.class, () -> service.getDetail(9L, 8L));
        assertEquals("无权查看该评测记录", otherUser.getMessage());

        service.stored.setUserId(null);
        BaseException cliRun = assertThrows(BaseException.class, () -> service.getDetail(9L, 7L));
        assertEquals("无权查看该评测记录", cliRun.getMessage());

        service.stored = null;
        BaseException missing = assertThrows(BaseException.class, () -> service.getDetail(9L, 7L));
        assertEquals("评测记录不存在", missing.getMessage());

        BaseException anonymous = assertThrows(BaseException.class, () -> service.getDetail(9L, null));
        assertEquals("无权查看评测记录", anonymous.getMessage());
    }

    @Test
    void pageRejectsAnonymousAndInvalidStatus() {
        StubRunService service = new StubRunService(null);

        BaseException anonymous = assertThrows(BaseException.class,
                () -> service.pageRuns(null, null, null, 1, 10));
        assertEquals("无权查看评测记录", anonymous.getMessage());

        BaseException invalid = assertThrows(BaseException.class,
                () -> service.pageRuns(7L, null, "DONE", 1, 10));
        assertEquals("评测状态无效", invalid.getMessage());
    }

    @Test
    void createAndFailureUpdateKeepStatusOutsideCallerTransaction() throws Exception {
        assertRequiresNew("createRunning", Long.class, Long.class, RagEvalDatasetSource.class,
                String.class, List.class, Map.class);
        assertRequiresNew("markSuccess", Long.class, EvalResult.class, long.class);
        assertRequiresNew("markFailed", Long.class, EvalResult.class, long.class, Throwable.class);

        PersistingRunService service = new PersistingRunService();
        EvalCase evalCase = new EvalCase();
        evalCase.setId(1);
        evalCase.setQuery("q");
        evalCase.setExpectedDocIds(List.of("100"));
        Long runId = service.createRunning(7L, 1L, RagEvalDatasetSource.UPLOAD, "cases.json",
                List.of(evalCase), Map.of("evalTopK", 5));

        assertEquals(99L, runId);
        assertEquals(RagEvalRunStatus.RUNNING.name(), service.saved.getStatus());
        assertTrue(service.saved.getDatasetSnapshot().contains("\"query\":\"q\""));

        EvalResult result = new EvalResult();
        result.setTotal(1);
        result.setHitRate(1.0d);
        result.setRecall(1.0d);
        result.setMrr(1.0d);
        service.markFailed(runId, result, 12L, new IllegalStateException("redis down"));

        assertEquals(RagEvalRunStatus.FAILED.name(), service.updated.getStatus());
        assertEquals(1.0d, service.updated.getHitRate());
        assertEquals(1.0d, service.updated.getRecallAtK());
        assertEquals("redis down", service.updated.getErrorMessage());
        assertEquals(12L, service.updated.getDurationMs());
    }

    private void assertRequiresNew(String name, Class<?>... parameterTypes) throws Exception {
        Method method = RagEvalRunService.class.getMethod(name, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    private static RagEvalRun ownedRun(Long id) {
        RagEvalRun run = new RagEvalRun();
        run.setId(id);
        run.setUserId(7L);
        run.setKnowledgeId(1L);
        run.setDatasetSource(RagEvalDatasetSource.UPLOAD.name());
        run.setDatasetName("cases.json");
        run.setStatus(RagEvalRunStatus.SUCCESS.name());
        run.setDatasetSnapshot("[{\"id\":1,\"query\":\"q\",\"expectedDocIds\":[\"100\"]}]");
        run.setConfigSnapshot("{\"evalTopK\":5}");
        run.setResultSnapshot("{\"total\":1,\"hitRate\":1.0,\"recall\":1.0,\"mrr\":1.0}");
        run.setTotalCases(1);
        run.setHitRate(1.0d);
        run.setRecallAtK(1.0d);
        return run;
    }

    private static final class StubRunService extends RagEvalRunService {
        private RagEvalRun stored;

        private StubRunService(RagEvalRun stored) {
            super(new ObjectMapper());
            this.stored = stored;
        }

        @Override
        public RagEvalRun getById(Serializable id) {
            if (stored == null || id == null || !id.equals(stored.getId())) {
                return null;
            }
            return stored;
        }
    }

    /** 替掉数据库写入，检查状态和指标有没有被组装进独立更新。 */
    private static final class PersistingRunService extends RagEvalRunService {
        private RagEvalRun saved;
        private RagEvalRun updated;

        private PersistingRunService() {
            super(new ObjectMapper());
        }

        @Override
        public boolean save(RagEvalRun entity) {
            entity.setId(99L);
            this.saved = entity;
            return true;
        }

        @Override
        public boolean updateById(RagEvalRun entity) {
            this.updated = entity;
            return true;
        }
    }
}
