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
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import com.zx.consultant.rag.eval.vo.EvalRunExecuteVO;
import com.zx.consultant.rag.retriever.HybridRetriever;
import com.zx.consultant.rag.retriever.HybridRetriever.RecallTrace;
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
    private final RecordingTraceService traceService = new RecordingTraceService();
    private RagEvalService ragEvalService;

    @BeforeEach
    void setUp() {
        ragEvalService = new RagEvalService(
                retriever, new ObjectMapper(), writer, runService, null, traceService);
        ReflectionTestUtils.setField(ragEvalService, "embeddingModelName", "text-embedding-v3");
        ReflectionTestUtils.setField(ragEvalService, "retrieverMaxResults", 8);
        ReflectionTestUtils.setField(ragEvalService, "retrieverMinScore", 0.75d);
        ReflectionTestUtils.setField(ragEvalService, "retrieverBm25MinScore", 1.0d);
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
        assertEquals(1, retriever.diagnoseCount);
        assertEquals(0, retriever.retrieveCount);
        EvalCaseResult caseResult = result.getCases().get(0);
        assertEquals(1, caseResult.getVectorHitCount());
        assertEquals(0, caseResult.getBm25HitCount());
        assertEquals("@knowledgeId:[1 1] @text:(q)", caseResult.getBm25Query());
        assertEquals(RetrievalMissStage.IN_EVAL_TOPK, caseResult.getMissStage());
        assertEquals(1, caseResult.getExpectedHits().get(0).getEvalDocRank());
        assertEquals(1, traceService.batches.size());
        assertEquals(11L, traceService.lastRunId);
        RagEvalRetrievalTrace row = traceService.batches.get(0).get(0);
        assertEquals(1, row.getVectorRank());
        assertNull(row.getBm25Rank());
        assertEquals(1, row.getEvalDocRank());
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
        assertTrue(traceService.batches.isEmpty());
        assertEquals(1, runService.failures.get(0).result.getCases().get(0).getVectorHitCount());
    }

    @Test
    void invalidArgumentsDoNotCreateRun() {
        assertThrows(IllegalStateException.class,
                () -> ragEvalService.evaluate(List.of(), 1L, "cases.json"));
        assertThrows(IllegalStateException.class,
                () -> ragEvalService.evaluate(List.of(evalCase()), null, "cases.json"));

        assertEquals(0, runService.createCount);
        assertEquals(0, retriever.retrieveCount);
        assertEquals(0, retriever.diagnoseCount);
        assertTrue(traceService.batches.isEmpty());
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
        assertTrue(traceService.batches.isEmpty());

        events.clear();
        runService.createError = null;
        IllegalStateException failure = new IllegalStateException("redis down");
        retriever.toThrow = failure;
        runService.failError = new RuntimeException("update failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ragEvalService.evaluate(List.of(evalCase()), 1L, "cases.json"));
        assertSame(failure, thrown);
    }

    @Test
    void traceSaveFailureDoesNotChangeMetricsOrSuccessStatus() {
        retriever.hits = List.of(chunk(100L));
        traceService.error = new RuntimeException("trace down");

        EvalResult result = ragEvalService.evaluate(List.of(evalCase()), 1L, "cases.json");

        assertEquals(1.0d, result.getHitRate());
        assertEquals(List.of("write", "success"), events);
        assertEquals(result, runService.successResult);
    }

    @Test
    void outsideVectorWindowDoesNotInventRrfRankAndStillSavesRealCandidates() {
        RecallTrace trace = new RecallTrace();
        List<RetrievedChunk> vectors = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            RetrievedChunk hit = chunk(1000L + i);
            hit.setChunkId(2000L + i);
            hit.setVectorScore(0.8d);
            vectors.add(hit);
        }
        trace.vectorHits = vectors;
        trace.bm25Hits = List.of();
        trace.bm25Query = "@knowledgeId:[1 1] @text:(threadlocal)";
        trace.mergedByRrf = new ArrayList<>(vectors);
        trace.afterFilter = new ArrayList<>(vectors);
        trace.finalTopK = new ArrayList<>(vectors);
        retriever.customTrace = trace;

        EvalCase evalCase = new EvalCase();
        evalCase.setId(34);
        evalCase.setQuery("ThreadLocal 存在哪一份文档");
        evalCase.setExpectedDocIds(List.of("999"));

        EvalResult result = ragEvalService.evaluate(List.of(evalCase), 1L, "cases.json");

        assertEquals(0.0d, result.getHitRate());
        assertEquals(0.0d, result.getRecall());
        assertEquals(0.0d, result.getMrr());
        assertEquals(1, retriever.diagnoseCount);
        assertEquals(0, retriever.retrieveCount);
        EvalCaseResult caseResult = result.getCases().get(0);
        assertEquals(Boolean.FALSE, caseResult.isHit());
        assertEquals(8, caseResult.getVectorHitCount());
        assertEquals(0, caseResult.getBm25HitCount());
        assertEquals(RetrievalMissStage.NOT_RECALLED, caseResult.getMissStage());
        ExpectedDocHit expected = caseResult.getExpectedHits().get(0);
        assertEquals("999", expected.getExpectedDocId());
        assertNull(expected.getVectorRank());
        assertNull(expected.getBm25Rank());
        assertNull(expected.getRrfRank());
        assertNull(expected.getFinalRank());
        assertNull(expected.getEvalDocRank());
        assertEquals(1, traceService.batches.size());
        List<RagEvalRetrievalTrace> rows = traceService.batches.get(0);
        assertEquals(8, rows.size());
        assertEquals(1, rows.get(0).getVectorRank());
        assertEquals(8, rows.get(7).getVectorRank());
        for (RagEvalRetrievalTrace row : rows) {
            assertNull(row.getBm25Rank());
            assertNull(row.getBm25Score());
            assertTrue(row.getDocumentId() == null || row.getDocumentId() != 999L);
        }
    }

    @Test
    void multipleExpectedDocsKeepSeparateStagesWithoutChangingMetrics() {
        retriever.hits = List.of(chunk(100L));
        EvalCase evalCase = evalCase();
        evalCase.setExpectedDocIds(List.of("100", "200"));

        EvalResult result = ragEvalService.evaluate(List.of(evalCase), 1L, "cases.json");

        assertEquals(1.0d, result.getHitRate());
        assertEquals(1.0d, result.getRecall());
        assertEquals(1.0d, result.getMrr());
        List<ExpectedDocHit> hits = result.getCases().get(0).getExpectedHits();
        assertEquals(2, hits.size());
        assertEquals(1, hits.get(0).getVectorRank());
        assertEquals(1, hits.get(0).getRrfRank());
        assertEquals(1, hits.get(0).getEvalDocRank());
        assertNull(hits.get(1).getVectorRank());
        assertNull(hits.get(1).getBm25Rank());
        assertNull(hits.get(1).getRrfRank());
        assertNull(hits.get(1).getEvalDocRank());
        assertEquals(RetrievalMissStage.IN_EVAL_TOPK, result.getCases().get(0).getMissStage());
        assertEquals(1, traceService.batches.get(0).size());
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
        private RecallTrace customTrace;
        private RuntimeException toThrow;
        private int retrieveCount;
        private int diagnoseCount;

        private RecordingRetriever() {
            super(null, null);
        }

        @Override
        public RecallTrace diagnose(String query, Long knowledgeId) {
            diagnoseCount++;
            if (toThrow != null) {
                throw toThrow;
            }
            return customTrace != null ? customTrace : snapshot(hits);
        }

        @Override
        public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
            retrieveCount++;
            if (toThrow != null) {
                throw toThrow;
            }
            return hits;
        }

        /** 桩只代表一次 diagnose，不再调用 retrieve。 */
        private static RecallTrace snapshot(List<RetrievedChunk> chunks) {
            RecallTrace trace = new RecallTrace();
            trace.vectorHits = new ArrayList<>(chunks);
            trace.bm25Hits = List.of();
            trace.bm25Query = "@knowledgeId:[1 1] @text:(q)";
            trace.mergedByRrf = new ArrayList<>(chunks);
            trace.afterFilter = new ArrayList<>(chunks);
            trace.finalTopK = new ArrayList<>(chunks);
            return trace;
        }
    }

    private static final class RecordingTraceService extends RagEvalRetrievalTraceService {
        private final List<List<RagEvalRetrievalTrace>> batches = new ArrayList<>();
        private Long lastRunId;
        private RuntimeException error;

        private RecordingTraceService() {
            super(null, null, null);
        }

        @Override
        public void saveRunTraces(Long runId, List<RagEvalRetrievalTrace> rows) {
            if (error != null) {
                throw error;
            }
            lastRunId = runId;
            batches.add(rows == null ? List.of() : List.copyOf(rows));
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
