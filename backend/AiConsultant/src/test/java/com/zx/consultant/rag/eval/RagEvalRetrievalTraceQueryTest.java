package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import com.zx.consultant.rag.eval.entity.RagEvalRun;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 链路查询沿用评测记录的归属校验，并按 runId + caseId 读取。
 */
class RagEvalRetrievalTraceQueryTest {

    @BeforeAll
    static void initMybatisTable() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                RagEvalRetrievalTrace.class);
    }

    @Test
    void queryChecksOwnerCaseAndDoesNotTreatHistoricalRunAsSaved() {
        RagEvalRun run = ownedRun();
        StubRunService runs = new StubRunService(run);
        StubTraceService traces = new StubTraceService(runs);

        RagEvalRetrievalTraceVO view = traces.getTrace(9L, 34, 7L);

        assertFalse(view.isSaved());
        assertTrue(view.getVectorHits().isEmpty());
        assertTrue(traces.lastSql.contains("run_id"));
        assertTrue(traces.lastSql.contains("case_id"));

        BaseException missingCase = assertThrows(BaseException.class, () -> traces.getTrace(9L, 16, 7L));
        assertEquals("评测用例不存在", missingCase.getMessage());

        BaseException otherUser = assertThrows(BaseException.class, () -> traces.getTrace(9L, 34, 8L));
        assertEquals("无权查看该评测记录", otherUser.getMessage());
    }

    @Test
    void savedRowsForTheRequestedCaseAreReturned() {
        RagEvalRun run = ownedRun();
        run.setDatasetSnapshot("[{\"id\":34,\"query\":\"q\",\"expectedDocIds\":[\"100\"]}]");
        run.setResultSnapshot("""
                {"total":1,"hitRate":0.0,"recall":0.0,"mrr":0.0,"failedCases":[],"cases":[
                {"id":34,"query":"q","expectedDocIds":["100"],"retrievedDocIds":[],"hit":false,
                "recall":0.0,"mrr":0.0,"vectorHitCount":1,"bm25HitCount":0,"bm25Query":"redis-q"}]}
                """);
        StubTraceService traces = new StubTraceService(new StubRunService(run));
        RagEvalRetrievalTrace current = row(34);
        current.setDocumentId(100L);
        current.setChunkId(1000L);
        current.setVectorRank(7);
        current.setVectorScore(0.81d);
        current.setRrfRank(4);
        current.setRrfScore(0.015d);
        current.setFiltered(0);
        current.setAfterFilterRank(4);
        current.setFinalRank(null);
        traces.stored = List.of(current, row(16));

        RagEvalRetrievalTraceVO view = traces.getTrace(9L, 34, 7L);

        assertTrue(view.isSaved());
        assertEquals(1, view.getVectorHitCount());
        assertEquals(0, view.getBm25HitCount());
        assertEquals("redis-q", view.getBm25Query());
        assertEquals(1, view.getVectorHits().size());
        assertEquals(7, view.getVectorHits().get(0).getVectorRank());
        assertEquals(0.81d, view.getVectorHits().get(0).getVectorScore());
        assertTrue(view.getBm25Hits().isEmpty());
        assertEquals(1, view.getRrfHits().size());
        assertEquals(7, view.getExpectedHits().get(0).getVectorRank());
        assertEquals(4, view.getExpectedHits().get(0).getRrfRank());
        assertNull(view.getExpectedHits().get(0).getFinalRank());
        assertEquals(RetrievalMissStage.NOT_IN_FINAL_TOPK, view.getMissStage());
    }

    @Test
    void saveUsesIndependentTransaction() throws Exception {
        Method method = RagEvalRetrievalTraceService.class.getMethod(
                "saveRunTraces", Long.class, List.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    private static RagEvalRetrievalTrace row(Integer caseId) {
        RagEvalRetrievalTrace row = new RagEvalRetrievalTrace();
        row.setRunId(9L);
        row.setCaseId(caseId);
        row.setDocumentId(caseId.longValue());
        row.setFiltered(0);
        row.setRrfRank(1);
        return row;
    }

    private static RagEvalRun ownedRun() {
        RagEvalRun run = new RagEvalRun();
        run.setId(9L);
        run.setUserId(7L);
        run.setDatasetSnapshot("[{\"id\":34,\"query\":\"q\",\"expectedDocIds\":[\"999\"]}]");
        run.setConfigSnapshot("{\"evalTopK\":5}");
        run.setResultSnapshot("""
                {"total":1,"hitRate":0.0,"recall":0.0,"mrr":0.0,"failedCases":[],"cases":[
                {"id":34,"query":"q","expectedDocIds":["999"],"retrievedDocIds":[],"hit":false,"recall":0.0,"mrr":0.0}]}
                """);
        return run;
    }

    private static final class StubRunService extends RagEvalRunService {
        private final RagEvalRun stored;

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

    private static final class StubTraceService extends RagEvalRetrievalTraceService {
        private List<RagEvalRetrievalTrace> stored = List.of();
        private String lastSql = "";

        private StubTraceService(RagEvalRunService runs) {
            super(runs, null, null);
        }

        @Override
        public List<RagEvalRetrievalTrace> list(Wrapper<RagEvalRetrievalTrace> queryWrapper) {
            lastSql = queryWrapper.getSqlSegment();
            return stored.stream()
                    .filter(row -> Long.valueOf(9L).equals(row.getRunId()) && Integer.valueOf(34).equals(row.getCaseId()))
                    .toList();
        }
    }
}
