package com.zx.consultant.rag.retriever;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.eval.EvalCaseResult;
import com.zx.consultant.rag.eval.RetrievalMissStage;
import com.zx.consultant.rag.eval.RetrievalTraceSnapshots;
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import com.zx.consultant.rag.retriever.HybridRetriever.RecallTrace;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * diagnose 只包住一次 retrieve，并把 ThreadLocal 清掉。
 */
class HybridRetrieverDiagnoseTest {

    @Test
    void diagnoseCapturesOneRetrievalAndClearsTrace() {
        AtomicInteger vectorCalls = new AtomicInteger();
        AtomicInteger bm25Calls = new AtomicInteger();
        HybridRetriever retriever = retriever(
                (query, knowledgeId) -> {
                    vectorCalls.incrementAndGet();
                    return List.of(
                            chunk(1L, 11L, 0.9d, null),
                            chunk(2L, 12L, 0.8d, null),
                            chunk(4L, 14L, 0.1d, null));
                },
                (query, knowledgeId) -> {
                    bm25Calls.incrementAndGet();
                    remember("@knowledgeId:[1 1] @text:(hello)");
                    return List.of(
                            chunk(2L, 12L, null, 3.0d),
                            chunk(3L, 13L, null, 2.0d));
                });

        RecallTrace trace = retriever.diagnose("hello", 1L);

        assertEquals(1, vectorCalls.get());
        assertEquals(1, bm25Calls.get());
        assertEquals("@knowledgeId:[1 1] @text:(hello)", trace.bm25Query);
        assertNull(traceLocal().get());
        assertEquals(3, trace.vectorHits.size());
        assertEquals(2, trace.bm25Hits.size());
        assertEquals(4, trace.finalTopK.size() + trace.filtered.size());

        EvalCaseResult summary = new EvalCaseResult();
        summary.setId(34);
        List<String> evalDocs = List.of("2", "1", "3");
        RetrievalTraceSnapshots.fillSummary(summary, trace, List.of("4", "999"), evalDocs);
        List<RagEvalRetrievalTrace> rows = RetrievalTraceSnapshots.toRows(1L, 34, trace, evalDocs);

        RagEvalRetrievalTrace dropped = rows.stream()
                .filter(row -> Long.valueOf(4L).equals(row.getDocumentId()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, dropped.getVectorRank());
        assertNull(dropped.getBm25Rank());
        assertEquals(4, dropped.getRrfRank());
        assertEquals(1, dropped.getFiltered());
        assertEquals("vector-only below min-score", dropped.getFilterReason());
        assertNull(dropped.getAfterFilterRank());
        assertNull(dropped.getFinalRank());
        assertEquals(RetrievalMissStage.FILTERED, RetrievalTraceSnapshots.stageOf(summary.getExpectedHits().get(0)));
        assertNull(summary.getExpectedHits().get(1).getVectorRank());
        assertNull(summary.getExpectedHits().get(1).getRrfRank());
        assertTrue(rows.stream().noneMatch(row -> Long.valueOf(999L).equals(row.getDocumentId())));

        RecallTrace second = retriever.diagnose("next", 1L);
        assertEquals(2, vectorCalls.get());
        assertEquals(3, trace.vectorHits.size());
        assertEquals(3, second.vectorHits.size());
        assertNull(traceLocal().get());
    }

    @Test
    void diagnoseClearsTraceWhenRetrievalFails() {
        HybridRetriever retriever = retriever(
                (query, knowledgeId) -> {
                    throw new IllegalStateException("vector down");
                },
                (query, knowledgeId) -> List.of());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> retriever.diagnose("q", 1L));

        assertEquals("vector down", thrown.getMessage());
        assertNull(traceLocal().get());
    }

    @Test
    void blankBm25QueryDoesNotLeak() {
        BM25Retriever retriever = new BM25Retriever("127.0.0.1", 1, "", new ObjectMapper());

        assertTrue(retriever.retrieve("   ", 1L, 8).isEmpty());
        assertNull(retriever.consumeExecutedQuery());

        @SuppressWarnings("unchecked")
        ThreadLocal<String> executed = (ThreadLocal<String>) ReflectionTestUtils.getField(
                BM25Retriever.class, "EXECUTED_QUERY");
        executed.set("leaked");
        assertEquals("leaked", retriever.consumeExecutedQuery());
        assertNull(executed.get());
    }

    private static HybridRetriever retriever(Channel vectorChannel, Channel bm25Channel) {
        VectorRetriever vector = new VectorRetriever(null, null) {
            @Override
            public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
                return vectorChannel.load(query, knowledgeId);
            }
        };
        BM25Retriever bm25 = new BM25Retriever("127.0.0.1", 1, "", new ObjectMapper()) {
            @Override
            public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
                return bm25Channel.load(query, knowledgeId);
            }
        };
        HybridRetriever retriever = new HybridRetriever(vector, bm25);
        ReflectionTestUtils.setField(retriever, "maxResults", 8);
        ReflectionTestUtils.setField(retriever, "rrfK", 60);
        ReflectionTestUtils.setField(retriever, "minScore", 0.75d);
        ReflectionTestUtils.setField(retriever, "bm25MinScore", 1.0d);
        return retriever;
    }

    private static void remember(String redisQuery) {
        @SuppressWarnings("unchecked")
        ThreadLocal<String> executed = (ThreadLocal<String>) ReflectionTestUtils.getField(
                BM25Retriever.class, "EXECUTED_QUERY");
        executed.set(redisQuery);
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<RecallTrace> traceLocal() {
        return (ThreadLocal<RecallTrace>) ReflectionTestUtils.getField(HybridRetriever.class, "TRACE");
    }

    private static RetrievedChunk chunk(Long documentId, Long chunkId, Double vectorScore, Double bm25Score) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkId(chunkId);
        chunk.setVectorScore(vectorScore);
        chunk.setBm25Score(bm25Score);
        chunk.setPage(1);
        return chunk;
    }

    @FunctionalInterface
    private interface Channel {
        List<RetrievedChunk> load(String query, Long knowledgeId);
    }
}
