package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO;
import com.zx.consultant.rag.retriever.HybridRetriever.FilteredCandidate;
import com.zx.consultant.rag.retriever.HybridRetriever.RecallTrace;
import org.junit.jupiter.api.Test;

/**
 * 检索明细只记录本次快照里真实出现的排名，缺失阶段保持为空。
 */
class RetrievalTraceSnapshotTest {

    @Test
    void filteredCandidateKeepsPreFilterRankAndRealReason() {
        RetrievedChunk kept = chunk(1L, 11L, 0.9d, null);
        RetrievedChunk dropped = chunk(2L, 22L, 0.2d, 0.3d);
        RecallTrace trace = new RecallTrace();
        trace.vectorHits = List.of(kept, dropped);
        trace.bm25Hits = List.of(dropped);
        trace.mergedByRrf = List.of(kept, dropped);
        trace.filtered = List.of(new FilteredCandidate(dropped, "both vectorScore and bm25Score below threshold"));
        trace.afterFilter = List.of(kept);
        trace.finalTopK = List.of(kept);

        List<RagEvalRetrievalTrace> rows = RetrievalTraceSnapshots.toRows(9L, 3, trace, List.of("1"));

        assertEquals(2, rows.size());
        RagEvalRetrievalTrace filtered = rows.get(1);
        assertEquals(2, filtered.getVectorRank());
        assertEquals(1, filtered.getBm25Rank());
        assertEquals(2, filtered.getRrfRank());
        assertEquals(0.3d, filtered.getBm25Score());
        assertEquals(1, filtered.getFiltered());
        assertEquals("both vectorScore and bm25Score below threshold", filtered.getFilterReason());
        assertNull(filtered.getAfterFilterRank());
        assertNull(filtered.getFinalRank());
        assertNull(filtered.getEvalDocRank());
        assertEquals(1, rows.get(0).getFinalRank());
        assertEquals(1, rows.get(0).getEvalDocRank());
        assertEquals(0, rows.get(0).getFiltered());
        assertNull(rows.get(0).getFilterReason());
        assertNull(rows.get(0).getBm25Rank());
    }

    @Test
    void bm25OnlyCandidateDoesNotGetVectorRank() {
        RetrievedChunk bm25Only = chunk(5L, 55L, null, 2.5d);
        RecallTrace trace = new RecallTrace();
        trace.vectorHits = List.of();
        trace.bm25Hits = List.of(bm25Only);
        trace.mergedByRrf = List.of(bm25Only);
        trace.afterFilter = List.of(bm25Only);
        trace.finalTopK = List.of(bm25Only);

        RagEvalRetrievalTrace row = RetrievalTraceSnapshots.toRows(1L, 1, trace, List.of("5")).get(0);

        assertNull(row.getVectorRank());
        assertNull(row.getVectorScore());
        assertEquals(1, row.getBm25Rank());
        assertEquals(2.5d, row.getBm25Score());
        assertEquals(1, row.getRrfRank());
    }

    @Test
    void truncatedCandidateKeepsAfterFilterRankWithoutFinalRank() {
        RetrievedChunk first = chunk(1L, 11L, 0.9d, null);
        RetrievedChunk second = chunk(2L, 22L, 0.8d, null);
        RecallTrace trace = new RecallTrace();
        trace.vectorHits = List.of(first, second);
        trace.bm25Hits = List.of();
        trace.mergedByRrf = List.of(first, second);
        trace.afterFilter = List.of(first, second);
        trace.finalTopK = List.of(first);

        EvalCaseResult caseResult = new EvalCaseResult();
        caseResult.setId(8);
        RetrievalTraceSnapshots.fillSummary(caseResult, trace, List.of("2"), List.of("1"));

        ExpectedDocHit hit = caseResult.getExpectedHits().get(0);
        assertEquals(2, hit.getVectorRank());
        assertEquals(2, hit.getRrfRank());
        assertEquals(Boolean.FALSE, hit.getFiltered());
        assertNull(hit.getFinalRank());
        assertNull(hit.getEvalDocRank());
        assertEquals(RetrievalMissStage.NOT_IN_FINAL_TOPK, caseResult.getMissStage());
        assertEquals(0, caseResult.getBm25HitCount());
    }

    @Test
    void mixedExpectedDocsDoNotCollapseIntoOneStage() {
        RetrievedChunk filtered = chunk(2L, 22L, 0.2d, null);
        RecallTrace trace = new RecallTrace();
        trace.vectorHits = List.of(filtered);
        trace.bm25Hits = List.of();
        trace.mergedByRrf = List.of(filtered);
        trace.filtered = List.of(new FilteredCandidate(filtered, "vector-only below min-score"));
        trace.afterFilter = List.of();
        trace.finalTopK = List.of();

        EvalCaseResult caseResult = new EvalCaseResult();
        caseResult.setId(37);
        RetrievalTraceSnapshots.fillSummary(caseResult, trace, List.of("2", "9"), List.of());

        assertEquals(2, caseResult.getExpectedHits().size());
        assertEquals(Boolean.TRUE, caseResult.getExpectedHits().get(0).getFiltered());
        assertEquals("vector-only below min-score", caseResult.getExpectedHits().get(0).getFilterReason());
        assertEquals(1, caseResult.getExpectedHits().get(0).getRrfRank());
        assertNull(caseResult.getExpectedHits().get(1).getVectorRank());
        assertNull(caseResult.getExpectedHits().get(1).getRrfRank());
        assertEquals(RetrievalMissStage.MIXED, caseResult.getMissStage());
    }

    @Test
    void duplicateChunksShareEvalDocRank() {
        RetrievedChunk first = chunk(7L, 71L, 0.9d, null);
        RetrievedChunk second = chunk(7L, 72L, 0.8d, null);
        RetrievedChunk other = chunk(8L, 81L, 0.7d, null);
        RecallTrace trace = new RecallTrace();
        trace.vectorHits = List.of(first, second, other);
        trace.mergedByRrf = List.of(first, second, other);
        trace.afterFilter = List.of(first, second, other);
        trace.finalTopK = List.of(first, second, other);

        List<RagEvalRetrievalTrace> rows = RetrievalTraceSnapshots.toRows(
                1L, 1, trace, List.of("7", "8"));

        assertEquals(1, rows.get(0).getEvalDocRank());
        assertEquals(1, rows.get(1).getEvalDocRank());
        assertEquals(2, rows.get(2).getEvalDocRank());
    }

    @Test
    void oldSnapshotAndEmptyTraceStayCompatible() throws Exception {
        String json = """
                {"id":34,"query":"q","expectedDocIds":["999"],"retrievedDocIds":[],\
                "hit":false,"recall":0.0,"mrr":0.0}
                """;
        EvalCaseResult historical = new ObjectMapper().readValue(json, EvalCaseResult.class);

        assertFalse(historical.isHit());
        assertNull(historical.getVectorHitCount());
        assertNull(historical.getBm25HitCount());
        assertNull(historical.getBm25Query());
        assertNull(historical.getExpectedHits());
        assertNull(historical.getMissStage());

        RagEvalRetrievalTraceVO missing = RetrievalTraceSnapshots.toView(
                1L, 34, historical, List.of("999"), List.of());
        assertFalse(missing.isSaved());
        assertTrue(missing.getVectorHits().isEmpty());
        assertTrue(missing.getExpectedHits().isEmpty());

        historical.setVectorHitCount(0);
        historical.setBm25HitCount(0);
        RagEvalRetrievalTraceVO emptyChannels = RetrievalTraceSnapshots.toView(
                1L, 34, historical, List.of("999"), List.of());
        assertTrue(emptyChannels.isSaved());
        assertEquals(0, emptyChannels.getVectorHitCount());
        assertEquals(0, emptyChannels.getBm25HitCount());
        assertEquals(RetrievalMissStage.NOT_RECALLED, emptyChannels.getMissStage());
        assertNull(emptyChannels.getExpectedHits().get(0).getRrfRank());
    }

    @Test
    void viewUsesOnlyProvidedRows() {
        RagEvalRetrievalTrace current = row(9L, 34, 1L);
        EvalCaseResult caseResult = new EvalCaseResult();
        caseResult.setVectorHitCount(1);
        caseResult.setBm25HitCount(0);

        RagEvalRetrievalTraceVO view = RetrievalTraceSnapshots.toView(
                9L, 34, caseResult, List.of("1"), List.of(current));

        assertTrue(view.isSaved());
        assertEquals(1, view.getRrfHits().size());
        assertEquals(1L, view.getRrfHits().get(0).getDocumentId());
    }

    private static RetrievedChunk chunk(Long documentId, Long chunkId, Double vectorScore, Double bm25Score) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkId(chunkId);
        chunk.setVectorScore(vectorScore);
        chunk.setBm25Score(bm25Score);
        chunk.setFinalScore(0.02d);
        chunk.setPage(1);
        chunk.setContent("不应写入明细表");
        return chunk;
    }

    private static RagEvalRetrievalTrace row(Long runId, Integer caseId, Long documentId) {
        RagEvalRetrievalTrace row = new RagEvalRetrievalTrace();
        row.setRunId(runId);
        row.setCaseId(caseId);
        row.setDocumentId(documentId);
        row.setChunkId(documentId);
        row.setVectorRank(1);
        row.setRrfRank(1);
        row.setFiltered(0);
        row.setFinalRank(1);
        row.setEvalDocRank(1);
        return row;
    }
}
