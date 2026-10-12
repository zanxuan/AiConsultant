package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.eval.vo.EvalRunExecuteVO;
import com.zx.consultant.rag.retriever.HybridRetriever;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 评测历史只挂在校验之后：成功更新、失败更新并抛出原异常、参数不合法不建记录。
 * 报告写入仍在成功落库之前，写报告失败时已算出的结果会进入失败记录。
 */
class RagEvalHistoryHookTest {

    private final List<String> events = new ArrayList<>();
    private final RecordingRetriever retriever = new RecordingRetriever();
    private final RecordingWriter writer = new RecordingWriter();
    private final RecordingRunService runService = new RecordingRunService();
    private RagEvalService ragEvalService;

    @BeforeEach
    void setUp() {
        ragEvalService = new RagEvalService(retriever, new ObjectMapper(), writer, runService, null);
        ReflectionTestUtils.setField(ragEvalService, "embeddingModelName", "text-embedding-v3");
        ReflectionTestUtils.setField(ragEvalService, "retrieverMaxResults", 8);
        ReflectionTestUtils.setField(ragEvalService, "retrieverMinScore", 0.75d);
        ReflectionTestUtils.setField(ragEvalService, "rrfK", 60);
        ReflectionTestUtils.setField(ragEvalService, "goldenSetPath", "docs/eval/golden-set.json");
        BaseContext.setCurrentId(7L);
        writer.events = events;
        runService.events = events;
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void successPersistsAfterReportAndKeepsMetrics() {
        retriever.hits = List.of(chunk(100L));

        EvalResult result = ragEvalService.evaluate(
                List.of(evalCase()), 1L, "golden-set.json", RagEvalDatasetSource.GOLDEN_SET);

        assertEquals(1, result.getTotal());
        assertEquals(1.0d, result.getHitRate());
        assertEquals(1.0d, result.getRecall());
        assertEquals(1.0d, result.getMrr());
        assertEquals(7L, runService.userId);
        assertEquals(1L, runService.knowledgeId);
        assertEquals("text-embedding-v3", runService.modelName);
        assertEquals(RagEvalDatasetSource.GOLDEN_SET, runService.datasetSource);
        assertEquals("golden-set.json", runService.datasetName);
        assertEquals(5, runService.config.get("evalTopK"));
        assertEquals("docs/eval/golden-set.json", runService.config.get("goldenSetPath"));
        assertEquals(List.of("write", "success"), events);
        assertEquals(result, runService.successResult);
        assertTrue(runService.failures.isEmpty());
    }

    @Test
    void executeReturnsRunIdWithTheSameMetrics() {
        retriever.hits = List.of(chunk(100L));

        EvalRunExecuteVO executed = ragEvalService.execute(List.of(evalCase()), 1L, "cases.json");

        assertEquals(11L, executed.getRunId());
        assertEquals(1.0d, executed.getResult().getHitRate());
        assertEquals(executed.getResult(), runService.successResult);
    }

    @Test
    void uploadSourceDoesNotStoreGoldenSetPath() {
        retriever.hits = List.of(chunk(100L));

        ragEvalService.evaluate(List.of(evalCase()), 1L, "cases.json");

        assertEquals(RagEvalDatasetSource.UPLOAD, runService.datasetSource);
        assertNull(runService.config.get("goldenSetPath"));
    }

    @Test
    void retrievalFailureMarksFailedAndRethrowsOriginal() {
        IllegalStateException failure = new IllegalStateException("redis down");
        retriever.toThrow = failure;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ragEvalService.evaluate(List.of(evalCase()), 1L, "cases.json"));

        assertSame(failure, thrown);
        assertEquals(1, runService.failures.size());
        assertNull(runService.failures.get(0).result);
        assertSame(failure, runService.failures.get(0).error);
        assertTrue(events.isEmpty());
        assertNull(runService.successResult);
    }

    @Test
    void reportFailureKeepsComputedResultAndRethrows() {
        retriever.hits = List.of(chunk(100L));
        IllegalStateException failure = new IllegalStateException("写入评测报告失败");
        writer.toThrow = failure;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ragEvalService.evaluate(List.of(evalCase()), 1L, "cases.json"));

        assertSame(failure, thrown);
        assertEquals(1, runService.failures.size());
        assertEquals(1, runService.failures.get(0).result.getTotal());
        assertEquals(1.0d, runService.failures.get(0).result.getHitRate());
        assertSame(failure, runService.failures.get(0).error);
        assertNull(runService.successResult);
        assertEquals(List.of("write"), events);
    }

    @Test
    void invalidArgumentsDoNotCreateRun() {
        assertThrows(IllegalStateException.class,
                () -> ragEvalService.evaluate(List.of(), 1L, "cases.json"));
        assertThrows(IllegalStateException.class,
                () -> ragEvalService.evaluate(List.of(evalCase()), null, "cases.json"));

        assertEquals(0, runService.createCount);
        assertEquals(0, retriever.retrieveCount);
        assertTrue(events.isEmpty());
    }

    @Test
    void historyWriteFailureDoesNotChangeEvalResultOrOriginalError() {
        runService.createError = new RuntimeException("db down");
        retriever.hits = List.of(chunk(100L));

        EvalResult result = ragEvalService.evaluate(List.of(evalCase()), 1L, "cases.json");

        assertEquals(1.0d, result.getHitRate());
        assertEquals(List.of("write"), events);
        assertNull(runService.successResult);
        assertTrue(runService.failures.isEmpty());

        events.clear();
        runService.createError = null;
        IllegalStateException failure = new IllegalStateException("redis down");
        retriever.toThrow = failure;
        runService.failError = new RuntimeException("update failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ragEvalService.evaluate(List.of(evalCase()), 1L, "cases.json"));
        assertSame(failure, thrown);
    }

    private static EvalCase evalCase() {
        EvalCase evalCase = new EvalCase();
        evalCase.setId(1);
        evalCase.setQuery("q");
        evalCase.setExpectedDocIds(List.of("100"));
        return evalCase;
    }

    private static RetrievedChunk chunk(Long documentId) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setDocumentId(documentId);
        chunk.setFinalScore(0.9d);
        return chunk;
    }

    private static final class RecordingRetriever extends HybridRetriever {
        private List<RetrievedChunk> hits = List.of();
        private RuntimeException toThrow;
        private int retrieveCount;

        private RecordingRetriever() {
            super(null, null);
        }

        @Override
        public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
            retrieveCount++;
            if (toThrow != null) {
                throw toThrow;
            }
            return hits;
        }
    }

    private static final class RecordingWriter extends EvalReportWriter {
        private RuntimeException toThrow;
        private List<String> events = new ArrayList<>();

        private RecordingWriter() {
            super(null);
        }

        @Override
        public Path write(EvalResult result) {
            events.add("write");
            if (toThrow != null) {
                throw toThrow;
            }
            return Path.of("noop");
        }
    }

    private static final class RecordingRunService extends RagEvalRunService {
        private final List<FailedMark> failures = new ArrayList<>();
        private List<String> events = new ArrayList<>();
        private RuntimeException createError;
        private RuntimeException failError;
        private int createCount;
        private Long userId;
        private Long knowledgeId;
        private String modelName;
        private String knowledgeName;
        private RagEvalDatasetSource datasetSource;
        private String datasetName;
        private Map<String, Object> config;
        private EvalResult successResult;

        private RecordingRunService() {
            super(new ObjectMapper());
        }

        @Override
        public Long createRunning(Long userId, Long knowledgeId, String modelName, String knowledgeName,
                RagEvalDatasetSource datasetSource, String datasetName, List<EvalCase> cases,
                Map<String, Object> configSnapshot) {
            createCount++;
            if (createError != null) {
                throw createError;
            }
            this.userId = userId;
            this.knowledgeId = knowledgeId;
            this.modelName = modelName;
            this.knowledgeName = knowledgeName;
            this.datasetSource = datasetSource;
            this.datasetName = datasetName;
            this.config = configSnapshot;
            return 11L;
        }

        @Override
        public void markSuccess(Long runId, EvalResult result, long durationMs, String modelName,
                String knowledgeName) {
            events.add("success");
            this.successResult = result;
            this.modelName = modelName;
            this.knowledgeName = knowledgeName;
        }

        @Override
        public void markFailed(Long runId, EvalResult result, long durationMs, Throwable error,
                String modelName, String knowledgeName) {
            if (failError != null) {
                throw failError;
            }
            this.modelName = modelName;
            this.knowledgeName = knowledgeName;
            failures.add(new FailedMark(result, error));
        }
    }

    private record FailedMark(EvalResult result, Throwable error) {
    }
}
