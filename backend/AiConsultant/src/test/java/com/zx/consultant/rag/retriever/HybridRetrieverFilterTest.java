package com.zx.consultant.rag.retriever;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.zx.consultant.rag.entity.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 融合后的候选过滤：两路原始分都低于各自阈值才丢弃，缺失分不按 0 处理。
 */
class HybridRetrieverFilterTest {

    private static final double VECTOR_MIN = 0.75d;
    private static final double BM25_MIN = 1.0d;

    private HybridRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new HybridRetriever(null, null);
        ReflectionTestUtils.setField(retriever, "maxResults", 8);
        ReflectionTestUtils.setField(retriever, "rrfK", 60);
        ReflectionTestUtils.setField(retriever, "minScore", VECTOR_MIN);
        ReflectionTestUtils.setField(retriever, "bm25MinScore", BM25_MIN);
    }

    @Test
    void filtersWhenBothScoresAreBelowThreshold() {
        List<RetrievedChunk> result = fuse(vector(1L, 0.74d), bm25(1L, 0.99d));

        assertTrue(result.isEmpty());
    }

    @Test
    void keepsWhenVectorMeetsThresholdAndBm25IsBelow() {
        List<RetrievedChunk> result = fuse(vector(1L, VECTOR_MIN), bm25(1L, 0.5d));

        assertEquals(1, result.size());
        assertEquals(VECTOR_MIN, result.get(0).getVectorScore());
        assertEquals(0.5d, result.get(0).getBm25Score());
    }

    @Test
    void keepsWhenVectorIsBelowAndBm25MeetsThreshold() {
        List<RetrievedChunk> result = fuse(vector(1L, 0.74d), bm25(1L, BM25_MIN));

        assertEquals(1, result.size());
        assertEquals(0.74d, result.get(0).getVectorScore());
        assertEquals(BM25_MIN, result.get(0).getBm25Score());
    }

    @Test
    void keepsWhenBothScoresMeetThreshold() {
        List<RetrievedChunk> result = fuse(vector(1L, 0.9d), bm25(1L, 2.0d));

        assertEquals(1, result.size());
        RetrievedChunk kept = result.get(0);
        assertEquals(1L, kept.getChunkId());
        assertEquals(0.9d, kept.getVectorScore());
        assertEquals(2.0d, kept.getBm25Score());
        assertTrue(kept.getFinalScore() > 0);
    }

    @Test
    void keepsVectorOnlyHitAtThresholdWhenBm25ScoreIsMissing() {
        RetrievedChunk hit = vector(1L, VECTOR_MIN);

        List<RetrievedChunk> result = retriever.fuseWithRrf(List.of(hit), List.of());

        assertEquals(1, result.size());
        assertEquals(VECTOR_MIN, result.get(0).getVectorScore());
        assertNull(result.get(0).getBm25Score());
        assertFalse(retriever.shouldFilter(result.get(0)));
    }

    @Test
    void keepsBm25OnlyHitAtThresholdWhenVectorScoreIsMissing() {
        RetrievedChunk hit = bm25(1L, BM25_MIN);

        List<RetrievedChunk> result = retriever.fuseWithRrf(List.of(), List.of(hit));

        assertEquals(1, result.size());
        assertNull(result.get(0).getVectorScore());
        assertEquals(BM25_MIN, result.get(0).getBm25Score());
        assertFalse(retriever.shouldFilter(result.get(0)));
    }

    @Test
    void filtersVectorOnlyHitBelowItsOwnThreshold() {
        List<RetrievedChunk> result = retriever.fuseWithRrf(List.of(vector(1L, 0.74d)), List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void filtersBm25OnlyHitBelowItsOwnThreshold() {
        List<RetrievedChunk> result = retriever.fuseWithRrf(List.of(), List.of(bm25(1L, 0.99d)));

        assertTrue(result.isEmpty());
    }

    @Test
    void filtersBeforeTopKSoLowScoreLeaderDoesNotOccupyTheSlot() {
        ReflectionTestUtils.setField(retriever, "maxResults", 1);
        RetrievedChunk lowButFirst = vector(1L, 0.1d);
        RetrievedChunk kept = vector(2L, 0.9d);

        List<RetrievedChunk> result = retriever.fuseWithRrf(
                List.of(lowButFirst, kept),
                List.of(bm25(1L, 0.1d), bm25(2L, 0.1d)));

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getChunkId());
    }

    private List<RetrievedChunk> fuse(RetrievedChunk vectorHit, RetrievedChunk bm25Hit) {
        return retriever.fuseWithRrf(List.of(vectorHit), List.of(bm25Hit));
    }

    private static RetrievedChunk vector(long chunkId, double vectorScore) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setChunkId(chunkId);
        chunk.setDocumentId(chunkId);
        chunk.setContent("vector-" + chunkId);
        chunk.setVectorScore(vectorScore);
        return chunk;
    }

    private static RetrievedChunk bm25(long chunkId, double bm25Score) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setChunkId(chunkId);
        chunk.setDocumentId(chunkId);
        chunk.setContent("bm25-" + chunkId);
        chunk.setBm25Score(bm25Score);
        return chunk;
    }
}
