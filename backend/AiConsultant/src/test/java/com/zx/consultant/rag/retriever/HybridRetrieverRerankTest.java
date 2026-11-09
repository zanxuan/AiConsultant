package com.zx.consultant.rag.retriever;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.retriever.HybridRetriever.RecallTrace;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 重排序只改融合截断后的顺序。关闭、失败、候选不足时仍是 RRF 顺序，finalScore 不被改写。
 */
class HybridRetrieverRerankTest {

    @Test
    void disabledKeepsRrfOrderAndDoesNotCallReranker() {
        AtomicInteger calls = new AtomicInteger();
        HybridRetriever retriever = retriever((query, chunks) -> {
            calls.incrementAndGet();
            return new ChunkReranker.RerankScores(new double[] {0.1d, 0.9d}, 5L, null);
        });

        RecallTrace trace = retriever.diagnose("持久化", 1L);

        assertEquals(0, calls.get());
        assertFalse(trace.rerankApplied);
        assertFalse(trace.rerankFallback);
        assertEquals(List.of(1L, 2L), ids(trace.finalTopK));
        assertNull(trace.finalTopK.get(0).getRerankScore());
    }

    @Test
    void enabledReordersByRerankScoreAndKeepsRrfScore() {
        HybridRetriever retriever = retriever((query, chunks) -> {
            assertEquals("持久化", query);
            assertEquals(List.of(1L, 2L), ids(chunks));
            return new ChunkReranker.RerankScores(new double[] {0.2d, 0.9d}, 15L, null);
        });
        ReflectionTestUtils.setField(retriever, "rerankEnabled", true);

        RecallTrace trace = retriever.diagnose("持久化", 1L);

        assertTrue(trace.rerankApplied);
        assertFalse(trace.rerankFallback);
        assertEquals(15L, trace.rerankLatencyMs);
        assertEquals(List.of(2L, 1L), ids(trace.finalTopK));
        assertEquals(0.9d, trace.finalTopK.get(0).getRerankScore());
        assertEquals(0.2d, trace.finalTopK.get(1).getRerankScore());
        // 名次变了，RRF 分仍挂在原来的切块上。向量第一名的 RRF 分更高
        assertTrue(trace.finalTopK.get(1).getFinalScore() > trace.finalTopK.get(0).getFinalScore());
        assertEquals(List.of(1L, 2L), ids(trace.afterFilter));
    }

    @Test
    void failureKeepsRrfOrderAndRecordsFallback() {
        HybridRetriever retriever = retriever((query, chunks) ->
                new ChunkReranker.RerankScores(null, 20L, "timeout"));
        ReflectionTestUtils.setField(retriever, "rerankEnabled", true);

        RecallTrace trace = retriever.diagnose("持久化", 1L);

        assertFalse(trace.rerankApplied);
        assertTrue(trace.rerankFallback);
        assertEquals("timeout", trace.rerankFallbackReason);
        assertEquals(20L, trace.rerankLatencyMs);
        assertEquals(List.of(1L, 2L), ids(trace.finalTopK));
        assertNull(trace.finalTopK.get(0).getRerankScore());
    }

    @Test
    void singleCandidateSkipsRerank() {
        AtomicInteger calls = new AtomicInteger();
        HybridRetriever retriever = retriever((query, chunks) -> {
            calls.incrementAndGet();
            return new ChunkReranker.RerankScores(new double[] {0.5d}, 1L, null);
        });
        ReflectionTestUtils.setField(retriever, "rerankEnabled", true);
        ReflectionTestUtils.setField(retriever, "maxResults", 1);

        RecallTrace trace = retriever.diagnose("持久化", 1L);

        assertEquals(0, calls.get());
        assertFalse(trace.rerankApplied);
        assertEquals(1, trace.finalTopK.size());
    }

    private static HybridRetriever retriever(ChunkReranker reranker) {
        VectorRetriever vector = new VectorRetriever(null, null) {
            @Override
            public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
                return List.of(chunk(1L, 0.9d, "向量靠前"), chunk(2L, 0.8d, "向量靠后"));
            }
        };
        BM25Retriever bm25 = new BM25Retriever("127.0.0.1", 1, "", new ObjectMapper()) {
            @Override
            public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
                return List.of();
            }
        };
        HybridRetriever retriever = new HybridRetriever(vector, bm25);
        ReflectionTestUtils.setField(retriever, "maxResults", 8);
        ReflectionTestUtils.setField(retriever, "rrfK", 60);
        ReflectionTestUtils.setField(retriever, "minScore", 0.75d);
        ReflectionTestUtils.setField(retriever, "bm25MinScore", 1.0d);
        retriever.setChunkReranker(reranker);
        return retriever;
    }

    private static RetrievedChunk chunk(long chunkId, double vectorScore, String content) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setChunkId(chunkId);
        chunk.setDocumentId(chunkId);
        chunk.setContent(content);
        chunk.setVectorScore(vectorScore);
        chunk.setPage(1);
        return chunk;
    }

    private static List<Long> ids(List<RetrievedChunk> chunks) {
        return chunks.stream().map(RetrievedChunk::getChunkId).toList();
    }
}
