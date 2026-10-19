package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO.RetrievalEvalDocVO;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO.RetrievalTraceChunkVO;
import com.zx.consultant.rag.retriever.HybridRetriever;

/**
 * 把一次 {@link HybridRetriever.RecallTrace} 收成可落库的候选行，以及详情接口的阶段列表。
 * 排名只用各阶段列表的真实顺序，缺失阶段保持 null。
 */
public final class RetrievalTraceSnapshots {

    private RetrievalTraceSnapshots() {
    }

    /**
     * 每个进入 RRF 候选集合的 chunk 一行。向量 TopK 之外的文档不会补进行，也不会写 RRF 排名。
     */
    public static List<RagEvalRetrievalTrace> toRows(Long runId, Integer caseId,
            HybridRetriever.RecallTrace trace, List<String> evalDocIds) {
        if (caseId == null || trace == null || trace.mergedByRrf == null || trace.mergedByRrf.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> vectorRanks = ranks(trace.vectorHits);
        Map<String, Integer> bm25Ranks = ranks(trace.bm25Hits);
        Map<String, Integer> afterRanks = ranks(trace.afterFilter);
        Map<String, Integer> finalRanks = ranks(trace.finalTopK);
        Map<String, String> filteredReasons = filteredReasons(trace.filtered);
        Map<String, Integer> evalRanks = evalRanks(evalDocIds);

        List<RagEvalRetrievalTrace> rows = new ArrayList<>();
        int rrfRank = 1;
        for (RetrievedChunk chunk : trace.mergedByRrf) {
            if (chunk == null) {
                rrfRank++;
                continue;
            }
            String key = chunkKey(chunk);
            RagEvalRetrievalTrace row = new RagEvalRetrievalTrace();
            row.setRunId(runId);
            row.setCaseId(caseId);
            row.setChunkId(chunk.getChunkId());
            row.setDocumentId(chunk.getDocumentId());
            row.setPage(chunk.getPage());

            Integer vectorRank = vectorRanks.get(key);
            row.setVectorRank(vectorRank);
            row.setVectorScore(vectorRank == null ? null : chunk.getVectorScore());

            Integer bm25Rank = bm25Ranks.get(key);
            row.setBm25Rank(bm25Rank);
            row.setBm25Score(bm25Rank == null ? null : chunk.getBm25Score());

            row.setRrfRank(rrfRank);
            row.setRrfScore(chunk.getFinalScore());

            // 只有过滤名单里、且没有进入过滤后列表的候选才算被过滤
            boolean filtered = afterRanks.get(key) == null && filteredReasons.containsKey(key);
            row.setFiltered(filtered ? 1 : 0);
            row.setFilterReason(filtered ? filteredReasons.get(key) : null);
            row.setAfterFilterRank(afterRanks.get(key));
            row.setFinalRank(finalRanks.get(key));
            row.setEvalDocRank(evalRanks.get(docId(chunk.getDocumentId())));
            rows.add(row);
            rrfRank++;
        }
        return rows;
    }

    /**
     * 轻量摘要写入 EvalCaseResult。通道数量用本次实际返回条数，不用最终 TopK 反推。
     */
    public static void fillSummary(EvalCaseResult caseResult, HybridRetriever.RecallTrace trace,
            List<String> expectedDocIds, List<String> evalDocIds) {
        if (caseResult == null) {
            return;
        }
        caseResult.setVectorHitCount(size(trace == null ? null : trace.vectorHits));
        caseResult.setBm25HitCount(size(trace == null ? null : trace.bm25Hits));
        caseResult.setBm25Query(trace == null ? null : trace.bm25Query);
        // 摘要只借用行上的排名，caseId 为空时用占位值，这批行不会落库
        Integer caseId = caseResult.getId() == null ? Integer.valueOf(0) : caseResult.getId();
        List<RagEvalRetrievalTrace> rows = toRows(null, caseId, trace, evalDocIds);
        List<ExpectedDocHit> hits = expectedFromRows(expectedDocIds, rows);
        caseResult.setExpectedHits(hits);
        caseResult.setMissStage(caseStage(hits));
    }

    /**
     * 用已保存的行组装各阶段列表。没有明细、且通道数量不是确认为 0 时，saved 为 false。
     */
    public static RagEvalRetrievalTraceVO toView(Long runId, Integer caseId, EvalCaseResult caseResult,
            List<String> expectedDocIds, List<RagEvalRetrievalTrace> rows) {
        List<RagEvalRetrievalTrace> safeRows = rows == null ? List.of() : rows;
        boolean countsPresent = caseResult != null && caseResult.getVectorHitCount() != null
                && caseResult.getBm25HitCount() != null;
        boolean emptyChannels = countsPresent
                && caseResult.getVectorHitCount() == 0
                && caseResult.getBm25HitCount() == 0;
        boolean saved = !safeRows.isEmpty() || emptyChannels;

        RagEvalRetrievalTraceVO view = new RagEvalRetrievalTraceVO();
        view.setRunId(runId);
        view.setCaseId(caseId);
        view.setSaved(saved);
        if (!saved) {
            return view;
        }

        if (countsPresent) {
            view.setVectorHitCount(caseResult.getVectorHitCount());
            view.setBm25HitCount(caseResult.getBm25HitCount());
            view.setBm25Query(caseResult.getBm25Query());
        } else {
            view.setVectorHitCount(countRank(safeRows, true));
            view.setBm25HitCount(countRank(safeRows, false));
        }

        List<ExpectedDocHit> hits = expectedFromRows(expectedDocIds, safeRows);
        view.setExpectedHits(hits);
        view.setMissStage(caseStage(hits));

        Map<String, Boolean> expected = new HashMap<>();
        if (expectedDocIds != null) {
            for (String docId : expectedDocIds) {
                if (docId != null) {
                    expected.put(docId, Boolean.TRUE);
                }
            }
        }
        Map<Integer, RetrievalEvalDocVO> evalDocs = new HashMap<>();
        for (RagEvalRetrievalTrace row : safeRows) {
            RetrievalTraceChunkVO chunk = toChunk(row);
            view.getRrfHits().add(chunk);
            if (chunk.getVectorRank() != null) {
                view.getVectorHits().add(chunk);
            }
            if (chunk.getBm25Rank() != null) {
                view.getBm25Hits().add(chunk);
            }
            if (chunk.getAfterFilterRank() != null) {
                view.getAfterFilter().add(chunk);
            }
            if (chunk.getFinalRank() != null) {
                view.getFinalTopK().add(chunk);
            }
            if (row.getEvalDocRank() != null && row.getDocumentId() != null) {
                evalDocs.computeIfAbsent(row.getEvalDocRank(), rank -> {
                    RetrievalEvalDocVO doc = new RetrievalEvalDocVO();
                    doc.setRank(rank);
                    doc.setDocumentId(row.getDocumentId());
                    doc.setExpected(expected.containsKey(String.valueOf(row.getDocumentId())));
                    return doc;
                });
            }
        }
        view.getVectorHits().sort(Comparator.comparing(RetrievalTraceChunkVO::getVectorRank));
        view.getBm25Hits().sort(Comparator.comparing(RetrievalTraceChunkVO::getBm25Rank));
        view.getRrfHits().sort(Comparator.comparing(RetrievalTraceChunkVO::getRrfRank,
                Comparator.nullsLast(Integer::compareTo)));
        view.getAfterFilter().sort(Comparator.comparing(RetrievalTraceChunkVO::getAfterFilterRank));
        view.getFinalTopK().sort(Comparator.comparing(RetrievalTraceChunkVO::getFinalRank));
        view.setEvalDocs(evalDocs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList());
        return view;
    }

    /** 单个期望文档能被快照确认的阶段。 */
    public static String stageOf(ExpectedDocHit hit) {
        if (hit == null) {
            return RetrievalMissStage.NOT_RECALLED;
        }
        if (hit.getEvalDocRank() != null) {
            return RetrievalMissStage.IN_EVAL_TOPK;
        }
        if (hit.getVectorRank() == null && hit.getBm25Rank() == null && hit.getRrfRank() == null) {
            return RetrievalMissStage.NOT_RECALLED;
        }
        if (Boolean.TRUE.equals(hit.getFiltered())) {
            return RetrievalMissStage.FILTERED;
        }
        if (hit.getFinalRank() == null) {
            return RetrievalMissStage.NOT_IN_FINAL_TOPK;
        }
        return RetrievalMissStage.NOT_IN_EVAL_TOPK;
    }

    /**
     * 用例级阶段：任一期望文档进入评测 TopK 则为命中；否则各文档阶段一致才给出该阶段。
     */
    public static String caseStage(List<ExpectedDocHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return RetrievalMissStage.NO_EXPECTED;
        }
        List<String> stages = new ArrayList<>();
        for (ExpectedDocHit hit : hits) {
            stages.add(stageOf(hit));
        }
        if (stages.contains(RetrievalMissStage.IN_EVAL_TOPK)) {
            return RetrievalMissStage.IN_EVAL_TOPK;
        }
        String first = stages.get(0);
        for (String stage : stages) {
            if (!Objects.equals(first, stage)) {
                return RetrievalMissStage.MIXED;
            }
        }
        return first;
    }

    private static List<ExpectedDocHit> expectedFromRows(List<String> expectedDocIds,
            List<RagEvalRetrievalTrace> rows) {
        List<ExpectedDocHit> hits = new ArrayList<>();
        if (expectedDocIds == null) {
            return hits;
        }
        for (String expectedDocId : expectedDocIds) {
            ExpectedDocHit hit = new ExpectedDocHit();
            hit.setExpectedDocId(expectedDocId);
            List<RagEvalRetrievalTrace> matched = new ArrayList<>();
            if (rows != null && expectedDocId != null) {
                for (RagEvalRetrievalTrace row : rows) {
                    if (expectedDocId.equals(docId(row.getDocumentId()))) {
                        matched.add(row);
                    }
                }
            }
            if (!matched.isEmpty()) {
                hit.setVectorRank(minRank(matched, RagEvalRetrievalTrace::getVectorRank));
                hit.setBm25Rank(minRank(matched, RagEvalRetrievalTrace::getBm25Rank));
                hit.setRrfRank(minRank(matched, RagEvalRetrievalTrace::getRrfRank));
                hit.setFinalRank(minRank(matched, RagEvalRetrievalTrace::getFinalRank));
                hit.setEvalDocRank(minRank(matched, RagEvalRetrievalTrace::getEvalDocRank));
                boolean survived = matched.stream().anyMatch(row -> !Integer.valueOf(1).equals(row.getFiltered()));
                boolean dropped = matched.stream().anyMatch(row -> Integer.valueOf(1).equals(row.getFiltered()));
                if (survived) {
                    hit.setFiltered(Boolean.FALSE);
                } else if (dropped) {
                    hit.setFiltered(Boolean.TRUE);
                    hit.setFilterReason(filterReasonOfBest(matched));
                }
            }
            hits.add(hit);
        }
        return hits;
    }

    private static String filterReasonOfBest(List<RagEvalRetrievalTrace> matched) {
        RagEvalRetrievalTrace best = null;
        for (RagEvalRetrievalTrace row : matched) {
            if (!Integer.valueOf(1).equals(row.getFiltered())) {
                continue;
            }
            if (best == null || rankValue(row.getRrfRank()) < rankValue(best.getRrfRank())) {
                best = row;
            }
        }
        return best == null ? null : best.getFilterReason();
    }

    private static int rankValue(Integer rank) {
        return rank == null ? Integer.MAX_VALUE : rank;
    }

    private static Integer minRank(List<RagEvalRetrievalTrace> rows,
            java.util.function.Function<RagEvalRetrievalTrace, Integer> rank) {
        Integer best = null;
        for (RagEvalRetrievalTrace row : rows) {
            Integer value = rank.apply(row);
            if (value == null) {
                continue;
            }
            if (best == null || value < best) {
                best = value;
            }
        }
        return best;
    }

    private static RetrievalTraceChunkVO toChunk(RagEvalRetrievalTrace row) {
        RetrievalTraceChunkVO chunk = new RetrievalTraceChunkVO();
        chunk.setChunkId(row.getChunkId());
        chunk.setDocumentId(row.getDocumentId());
        chunk.setPage(row.getPage());
        chunk.setVectorRank(row.getVectorRank());
        chunk.setVectorScore(row.getVectorScore());
        chunk.setBm25Rank(row.getBm25Rank());
        chunk.setBm25Score(row.getBm25Score());
        chunk.setRrfRank(row.getRrfRank());
        chunk.setRrfScore(row.getRrfScore());
        chunk.setFiltered(Integer.valueOf(1).equals(row.getFiltered()));
        chunk.setFilterReason(row.getFilterReason());
        chunk.setAfterFilterRank(row.getAfterFilterRank());
        chunk.setFinalRank(row.getFinalRank());
        chunk.setEvalDocRank(row.getEvalDocRank());
        return chunk;
    }

    private static int countRank(List<RagEvalRetrievalTrace> rows, boolean vector) {
        int count = 0;
        for (RagEvalRetrievalTrace row : rows) {
            if (vector && row.getVectorRank() != null) {
                count++;
            }
            if (!vector && row.getBm25Rank() != null) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, Integer> ranks(List<RetrievedChunk> chunks) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        if (chunks == null) {
            return ranks;
        }
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            if (chunk == null) {
                continue;
            }
            ranks.putIfAbsent(chunkKey(chunk), i + 1);
        }
        return ranks;
    }

    private static Map<String, String> filteredReasons(List<HybridRetriever.FilteredCandidate> filtered) {
        Map<String, String> reasons = new LinkedHashMap<>();
        if (filtered == null) {
            return reasons;
        }
        for (HybridRetriever.FilteredCandidate item : filtered) {
            if (item == null || item.chunk == null) {
                continue;
            }
            reasons.putIfAbsent(chunkKey(item.chunk), item.reason);
        }
        return reasons;
    }

    private static Map<String, Integer> evalRanks(List<String> evalDocIds) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        if (evalDocIds == null) {
            return ranks;
        }
        for (int i = 0; i < evalDocIds.size(); i++) {
            if (evalDocIds.get(i) != null) {
                ranks.putIfAbsent(evalDocIds.get(i), i + 1);
            }
        }
        return ranks;
    }

    /** 与 HybridRetriever 的 chunk 去重键一致，避免两路同一 chunk 被记成两行。 */
    private static String chunkKey(RetrievedChunk chunk) {
        if (chunk.getChunkId() != null) {
            return "c:" + chunk.getChunkId();
        }
        int contentHash = chunk.getContent() == null ? 0 : chunk.getContent().hashCode();
        return "d:" + chunk.getDocumentId() + ":p:" + chunk.getPage() + ":h:" + contentHash;
    }

    private static String docId(Long documentId) {
        return documentId == null ? null : String.valueOf(documentId);
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
