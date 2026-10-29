package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.eval.ChunkEvalMetrics.ChunkMatch;
import com.zx.consultant.rag.retriever.HybridRetriever;
import com.zx.consultant.rag.retriever.HybridRetriever.RecallTrace;
import org.junit.jupiter.api.Test;

/**
 * Chunk 打分不连接数据库或 Redis。服务测试只用检索结果桩，确认 calculate 真正使用标准 chunk。
 */
class ChunkEvalMetricsTest {

    private static final double DELTA = 1e-9d;
    private static final long SNOWFLAKE = 2113438807872655361L;

    @Test
    void singleChunkHit() {
        ChunkMatch match = match(List.of("1"), ids(1L, 2L, 3L, 4L, 5L));

        assertScore(match, true, 1.0d, 1.0d);
        assertEquals(List.of("1", "2", "3", "4", "5"), match.retrievedChunkIds());
    }

    @Test
    void partialHitKeepsRecallSeparateFromHit() {
        ChunkMatch match = match(List.of("1", "2"), ids(1L, 3L, 4L, 5L, 6L));

        assertScore(match, true, 0.5d, 1.0d);
        assertNull(match.scoreNote());
    }

    @Test
    void allGroundTruthChunksHitAtLaterRank() {
        ChunkMatch match = match(List.of("1", "2"), ids(3L, 2L, 1L, 4L, 5L));

        assertScore(match, true, 1.0d, 0.5d);
    }

    @Test
    void sameDocumentDifferentChunkIsMiss() {
        List<RetrievedChunk> retrieved = List.of(
                chunk(9L, 2L), chunk(9L, 3L), chunk(9L, 4L), chunk(9L, 5L), chunk(9L, 6L));

        ChunkMatch match = match(List.of("1"), retrieved);

        assertScore(match, false, 0.0d, 0.0d);
    }

    @Test
    void hitOnlyAtSixthPositionIsOutsideTopFive() {
        ChunkMatch match = match(List.of("1"), ids(2L, 3L, 4L, 5L, 6L, 1L));

        assertScore(match, false, 0.0d, 0.0d);
        assertEquals(5, match.retrievedChunkIds().size());
        assertFalse(match.retrievedChunkIds().contains("1"));
    }

    @Test
    void emptyGroundTruthDoesNotDivideOrMatch() {
        ChunkMatch match = match(List.of(), ids(1L, 2L, 3L, 4L, 5L));

        assertScore(match, false, 0.0d, 0.0d);
        assertEquals(ChunkEvalMetrics.EMPTY_GROUND_TRUTH_NOTE, match.scoreNote());
        assertTrue(match.groundTruthChunkIds().isEmpty());
    }

    @Test
    void blankGroundTruthIdsAreTreatedAsEmptySet() {
        ChunkMatch match = match(List.of("  ", ""), ids(1L));

        assertScore(match, false, 0.0d, 0.0d);
        assertEquals(ChunkEvalMetrics.EMPTY_GROUND_TRUTH_NOTE, match.scoreNote());
    }

    @Test
    void duplicateGroundTruthCountsOnce() {
        ChunkMatch match = match(List.of("1", " 1 ", "2"), ids(1L, 3L, 4L, 5L, 6L));

        assertScore(match, true, 0.5d, 1.0d);
        assertEquals(List.of("1", "2"), match.groundTruthChunkIds());
    }

    @Test
    void duplicateRetrievedChunkDoesNotInflateRecall() {
        ChunkMatch match = match(List.of("1", "2"), ids(1L, 1L, 3L, 4L, 5L));

        assertScore(match, true, 0.5d, 1.0d);
    }

    @Test
    void nullChunkIdKeepsItsRank() {
        List<RetrievedChunk> retrieved = new ArrayList<>();
        retrieved.add(chunk(9L, null));
        retrieved.add(chunk(9L, 1L));
        retrieved.add(chunk(8L, 3L));
        retrieved.add(chunk(8L, 4L));
        retrieved.add(chunk(8L, 5L));

        ChunkMatch match = match(List.of("1"), retrieved);

        assertScore(match, true, 1.0d, 0.5d);
        assertNull(match.retrievedChunkIds().get(0));
        assertEquals("1", match.retrievedChunkIds().get(1));
    }

    @Test
    void snowflakeChunkIdStaysDecimalText() {
        ChunkMatch match = match(List.of(String.valueOf(SNOWFLAKE)), List.of(chunk(9L, SNOWFLAKE)));

        assertScore(match, true, 1.0d, 1.0d);
        assertEquals("2113438807872655361", match.retrievedChunkIds().get(0));
    }

    @Test
    void calculateUsesChunkIdsAndKeepsDocumentFallback() {
        Map<String, List<RetrievedChunk>> byQuery = Map.of(
                "partial", List.of(chunk(900L, 11L), chunk(901L, 21L)),
                "sibling", List.of(chunk(900L, 12L)),
                "sixth", sixthWindow(),
                "empty", List.of(chunk(900L, 11L)),
                "legacy", List.of(chunk(900L, 12L)));
        RagEvalService service = new RagEvalService(
                new FixedRetriever(byQuery), new ObjectMapper(), new NoopWriter(), new NoopRunService(), null, null);

        EvalResult result = service.evaluate(List.of(
                chunkCase(1, "partial", List.of("11", "12"), List.of("900")),
                chunkCase(2, "sibling", List.of("11"), List.of("900")),
                chunkCase(3, "sixth", List.of("11"), null),
                chunkCase(4, "empty", List.of(), List.of("900")),
                legacyCase()), 1L, "chunk-set.json");

        assertEquals(0.4d, result.getHitRate(), DELTA);
        assertEquals(0.3d, result.getRecall(), DELTA);
        assertEquals(0.4d, result.getMrr(), DELTA);
        assertEquals(3, result.getFailedCases().size());

        EvalCaseResult partial = result.getCases().get(0);
        assertTrue(partial.isHit());
        assertEquals(0.5d, partial.getRecall(), DELTA);
        assertEquals(1.0d, partial.getMrr(), DELTA);
        assertEquals(List.of("900", "901"), partial.getRetrievedDocIds());
        assertEquals(List.of("11", "12"), partial.getGroundTruthChunkIds());
        assertEquals(List.of("11", "21"), partial.getRetrievedChunkIds());
        assertTrue(result.getFailedCases().stream().noneMatch(item -> Integer.valueOf(1).equals(item.getId())));

        EvalCaseResult sibling = result.getCases().get(1);
        assertFalse(sibling.isHit());
        assertEquals(0.0d, sibling.getRecall(), DELTA);
        assertEquals(List.of("900"), sibling.getRetrievedDocIds());
        assertEquals(List.of("12"), sibling.getRetrievedChunkIds());
        assertEquals(List.of("900"), sibling.getExpectedDocIds());

        EvalCaseResult sixth = result.getCases().get(2);
        assertFalse(sixth.isHit());
        assertEquals(5, sixth.getRetrievedChunkIds().size());
        assertFalse(sixth.getRetrievedChunkIds().contains("11"));

        EvalCaseResult empty = result.getCases().get(3);
        assertFalse(empty.isHit());
        assertEquals(0.0d, empty.getRecall(), DELTA);
        assertEquals(ChunkEvalMetrics.EMPTY_GROUND_TRUTH_NOTE, empty.getScoreNote());
        FailedCase emptyFailed = result.getFailedCases().stream()
                .filter(item -> Integer.valueOf(4).equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("900"), emptyFailed.getExpectedDocIds());
        assertEquals(List.of("900"), emptyFailed.getRetrievedDocIds());
        assertEquals(ChunkEvalMetrics.EMPTY_GROUND_TRUTH_NOTE, emptyFailed.getScoreNote());
        assertTrue(emptyFailed.getGroundTruthChunkIds().isEmpty());

        EvalCaseResult legacy = result.getCases().get(4);
        assertTrue(legacy.isHit());
        assertEquals(1.0d, legacy.getRecall(), DELTA);
        assertEquals(1.0d, legacy.getMrr(), DELTA);
        assertNull(legacy.getGroundTruthChunkIds());
        assertNull(legacy.getRetrievedChunkIds());
        assertNull(legacy.getScoreNote());
        assertEquals(List.of("900"), legacy.getRetrievedDocIds());
    }

    private static ChunkMatch match(List<String> groundTruth, List<RetrievedChunk> retrieved) {
        return ChunkEvalMetrics.match(groundTruth, retrieved, 5);
    }

    private static void assertScore(ChunkMatch match, boolean hit, double recall, double reciprocalRank) {
        assertEquals(hit, match.hit());
        assertEquals(recall, match.recall(), DELTA);
        assertEquals(reciprocalRank, match.reciprocalRank(), DELTA);
    }

    private static List<RetrievedChunk> ids(Long... chunkIds) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (Long chunkId : chunkIds) {
            chunks.add(chunk(chunkId, chunkId));
        }
        return chunks;
    }

    private static List<RetrievedChunk> sixthWindow() {
        List<RetrievedChunk> chunks = ids(2L, 3L, 4L, 5L, 6L);
        chunks.add(chunk(900L, 11L));
        return chunks;
    }

    private static RetrievedChunk chunk(Long documentId, Long chunkId) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkId(chunkId);
        chunk.setFinalScore(0.9d);
        return chunk;
    }

    private static EvalCase chunkCase(int id, String query, List<String> groundTruth, List<String> expectedDocs) {
        EvalCase evalCase = new EvalCase();
        evalCase.setId(id);
        evalCase.setQuery(query);
        evalCase.setGroundTruthChunkIds(groundTruth);
        evalCase.setExpectedDocIds(expectedDocs);
        return evalCase;
    }

    private static EvalCase legacyCase() {
        EvalCase evalCase = new EvalCase();
        evalCase.setId(5);
        evalCase.setQuery("legacy");
        evalCase.setExpectedDocIds(List.of("900"));
        return evalCase;
    }

    private static final class FixedRetriever extends HybridRetriever {
        private final Map<String, List<RetrievedChunk>> byQuery;

        private FixedRetriever(Map<String, List<RetrievedChunk>> byQuery) {
            super(null, null);
            this.byQuery = byQuery;
        }

        @Override
        public RecallTrace diagnose(String query, Long knowledgeId) {
            RecallTrace trace = new RecallTrace();
            List<RetrievedChunk> chunks = byQuery.getOrDefault(query, List.of());
            trace.finalTopK = new ArrayList<>(chunks);
            trace.vectorHits = new ArrayList<>(chunks);
            trace.bm25Hits = List.of();
            trace.mergedByRrf = new ArrayList<>(chunks);
            trace.afterFilter = new ArrayList<>(chunks);
            return trace;
        }
    }

    private static final class NoopRunService extends RagEvalRunService {
        private NoopRunService() {
            super(new ObjectMapper());
        }

        @Override
        public Long createRunning(Long userId, Long knowledgeId, String modelName, String knowledgeName,
                RagEvalDatasetSource datasetSource, String datasetName, List<EvalCase> cases,
                Map<String, Object> configSnapshot) {
            return 1L;
        }

        @Override
        public void markSuccess(Long runId, EvalResult result, long durationMs, String modelName,
                String knowledgeName) {
        }

        @Override
        public void markFailed(Long runId, EvalResult result, long durationMs, Throwable error,
                String modelName, String knowledgeName) {
        }
    }

    private static final class NoopWriter extends EvalReportWriter {
        private NoopWriter() {
            super(null);
        }

        @Override
        public Path write(EvalResult result) {
            return Path.of("noop");
        }
    }
}
