package com.zx.consultant.rag.retriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.zx.consultant.rag.entity.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 混合检索器：向量召回 + BM25 关键词召回，经 RRF 融合后取 TopK。
 * <p>
 * RRF 只依赖名次，不比较 vectorScore 与 bm25Score（二者量纲不同）。
 * {@code finalScore(d) = Σ 1 / (k + rank_i(d))}
 * <p>
 * 融合后按两路原始分过滤：只有向量分和 BM25 分都低于各自阈值时才丢弃。
 * 缺失的一路分数不按 0 处理。无结果时的日志仍保留，低分短路由 RetrieveNode 决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final BM25Retriever bm25Retriever;

    @Value("${app.rag.retriever.max-results:5}")
    private Integer maxResults;

    /** RRF 平滑常数，经典默认 60 */
    @Value("${app.rag.retriever.rrf-k:60}")
    private Integer rrfK;

    /** 向量相似度下限，约 0~1。只和 vectorScore 比较，不替代 BM25 阈值 */
    @Value("${app.rag.retriever.min-score:0.75}")
    private Double minScore;

    /**
     * RediSearch 原始 BM25 下限。该分无上界，不能复用 min-score。
     */
    @Value("${app.rag.retriever.bm25-min-score:1.0}")
    private Double bm25MinScore;

    /** 仅诊断线程写入；为空时 retrieve() 不采集中间结果 */
    private static final ThreadLocal<RecallTrace> TRACE = new ThreadLocal<>();

    /**
     * 混合检索
     * @param query 查询词
     * @param knowledgeId 知识库ID
     * @return 检索结果
     */
    public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
        List<RetrievedChunk> vectorHits = vectorRetriever.retrieve(query, knowledgeId);
        List<RetrievedChunk> bm25Hits = bm25Retriever.retrieve(query, knowledgeId);
        // 无论是否在采集快照，都读走并清掉 BM25 查询串，避免线程复用残留
        String bm25Query = bm25Retriever.consumeExecutedQuery();

        RecallTrace trace = TRACE.get();
        if (trace != null) {
            trace.vectorHits = copyAll(vectorHits);
            trace.bm25Hits = copyAll(bm25Hits);
            trace.bm25Query = bm25Query;
        }

        log.info("Hybrid 检索开始, knowledgeId={}, vectorHits={}, bm25Hits={}, rrfK={}, topK={}",
                knowledgeId, vectorHits.size(), bm25Hits.size(), rrfK, maxResults);

        List<RetrievedChunk> ranked = fuseWithRrf(vectorHits, bm25Hits);

        if (trace != null) {
            trace.finalTopK = copyAll(ranked);
        }

        if (ranked.isEmpty()) {
            log.info("Hybrid 情况1: 无召回结果, knowledgeId={}", knowledgeId);
        } else {
            log.info("Hybrid 召回通过, knowledgeId={}, size={}, maxVectorScore={}, vectorMinScore={}, bm25MinScore={}",
                    knowledgeId, ranked.size(), maxVectorScore(ranked), minScore, bm25MinScore);
        }
        return ranked;
    }

    /**
     * 走与 {@link #retrieve(String, Long)} 完全相同的调用链，额外带回两路召回、融合和过滤快照。
     * 不改变返回的最终列表。
     */
    public RecallTrace diagnose(String query, Long knowledgeId) {
        RecallTrace trace = new RecallTrace();
        TRACE.set(trace);
        try {
            List<RetrievedChunk> ranked = retrieve(query, knowledgeId);
            // 正式 retrieve 会写入 finalTopK。覆盖了 retrieve 且没写快照时，仍使用这一次的返回值。
            if (trace.finalTopK.isEmpty() && ranked != null && !ranked.isEmpty()) {
                trace.finalTopK = copyAll(ranked);
            }
            return trace;
        } finally {
            TRACE.remove();
        }
    }

    /** 结果是否为空（情况1） */
    public boolean isEmpty(List<RetrievedChunk> docs) {
        return docs == null || docs.isEmpty();
    }

    /**
     * 最高向量分是否低于阈值（情况2）。
     * 无 vectorScore 时按 0 处理（例如仅 BM25 命中）。
     */
    public boolean isScoreTooLow(List<RetrievedChunk> docs) {
        if (isEmpty(docs)) {
            return false;
        }
        return maxVectorScore(docs) < minScore;
    }

    /**
     * 最高向量分
     * @param docs 检索结果
     * @return 最高向量分
     */
    private double maxVectorScore(List<RetrievedChunk> docs) {
        return docs.stream()
                .map(RetrievedChunk::getVectorScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }

    /**
     * Reciprocal Rank Fusion：按各通道排名累加 1/(k+rank)，合并同 chunk 的通道分。
     * 先按两路原始分过滤，再截断 TopK，避免被过滤的候选占住名额。
     * @param vectorHits 向量召回结果
     * @param bm25Hits BM25召回结果
     * @return 融合结果
     */
    List<RetrievedChunk> fuseWithRrf(
            List<RetrievedChunk> vectorHits,
            List<RetrievedChunk> bm25Hits) {

        Map<String, RetrievedChunk> merged = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        accumulate(vectorHits, merged, rrfScores, true);
        accumulate(bm25Hits, merged, rrfScores, false);

        List<RetrievedChunk> ranked = new ArrayList<>();
        RecallTrace trace = TRACE.get();
        List<RetrievedChunk> mergedSnapshot = new ArrayList<>();
        for (RetrievedChunk chunk : merged.values()) {
            Double score = rrfScores.get(chunkKey(chunk));
            chunk.setFinalScore(score);
            if (trace != null) {
                mergedSnapshot.add(copyOf(chunk));
            }
            // 过滤看 vectorScore / bm25Score，不用上面的 RRF 分
            if (!shouldFilter(chunk)) {
                ranked.add(chunk);
            } else if (trace != null) {
                trace.filtered.add(new FilteredCandidate(copyOf(chunk), filterReason(chunk)));
            }
        }
        if (trace != null) {
            mergedSnapshot.sort(Comparator.comparing(
                    RetrievedChunk::getFinalScore,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            trace.mergedByRrf = mergedSnapshot;
        }

        ranked.sort(Comparator.comparing(
                RetrievedChunk::getFinalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        if (trace != null) {
            trace.afterFilter = copyAll(ranked);
        }

        int limit = Math.min(maxResults, ranked.size());
        return new ArrayList<>(ranked.subList(0, limit));
    }

    /**
     * 两路原始分都有时，只有都严格小于各自阈值才过滤；等于阈值保留。
     * 只被一路召回时，缺失分不当成 0：已有分达到阈值就保留，低于该路阈值才过滤。
     */
    boolean shouldFilter(RetrievedChunk chunk) {
        if (chunk == null) {
            return true;
        }
        Double vectorScore = chunk.getVectorScore();
        Double bm25Score = chunk.getBm25Score();
        double vectorThreshold = minScore == null ? 0.75d : minScore;
        double bm25Threshold = bm25MinScore == null ? 1.0d : bm25MinScore;

        if (vectorScore != null && bm25Score != null) {
            return vectorScore < vectorThreshold && bm25Score < bm25Threshold;
        }
        if (vectorScore != null) {
            return vectorScore < vectorThreshold;
        }
        if (bm25Score != null) {
            return bm25Score < bm25Threshold;
        }
        return false;
    }

    /** 与 {@link #shouldFilter(RetrievedChunk)} 同一套规则，只解释原因，不改变过滤结果。 */
    String filterReason(RetrievedChunk chunk) {
        if (chunk == null) {
            return "chunk is null";
        }
        Double vectorScore = chunk.getVectorScore();
        Double bm25Score = chunk.getBm25Score();
        double vectorThreshold = minScore == null ? 0.75d : minScore;
        double bm25Threshold = bm25MinScore == null ? 1.0d : bm25MinScore;
        if (vectorScore != null && bm25Score != null) {
            return vectorScore < vectorThreshold && bm25Score < bm25Threshold
                    ? "both vectorScore and bm25Score below threshold"
                    : "kept";
        }
        if (vectorScore != null) {
            return vectorScore < vectorThreshold
                    ? "vector-only below min-score"
                    : "kept";
        }
        if (bm25Score != null) {
            return bm25Score < bm25Threshold
                    ? "bm25-only below bm25-min-score"
                    : "kept";
        }
        return "no scores";
    }

    /**
     * 累加RRF分数
     * @param hits 检索结果
     * @param merged 合并结果
     * @param rrfScores RRF分数
     * @param fromVector 是否来自向量通道
     */
    private void accumulate(
            List<RetrievedChunk> hits,
            Map<String, RetrievedChunk> merged,
            Map<String, Double> rrfScores,
            boolean fromVector) {

        int k = rrfK == null || rrfK < 1 ? 60 : rrfK;
        for (int i = 0; i < hits.size(); i++) {
            RetrievedChunk hit = hits.get(i);
            String key = chunkKey(hit);
            int rank = i + 1; // 1-based
            double contribution = 1.0 / (k + rank);

            rrfScores.merge(key, contribution, Double::sum);

            RetrievedChunk existing = merged.get(key);
            if (existing == null) {
                merged.put(key, hit);
                continue;
            }
            // 同 chunk 出现在两路：保留两侧分数与已有正文/元数据
            if (fromVector) {
                if (hit.getVectorScore() != null) {
                    existing.setVectorScore(hit.getVectorScore());
                }
            } else {
                if (hit.getBm25Score() != null) {
                    existing.setBm25Score(hit.getBm25Score());
                }
            }
            if (existing.getContent() == null && hit.getContent() != null) {
                existing.setContent(hit.getContent());
            }
            if (existing.getDocumentId() == null) {
                existing.setDocumentId(hit.getDocumentId());
            }
            if (existing.getPage() == null) {
                existing.setPage(hit.getPage());
            }
            if (existing.getChunkId() == null) {
                existing.setChunkId(hit.getChunkId());
            }
        }
    }

    /** 优先 chunkId；缺失时退化为 documentId + page + content 指纹 */
    private String chunkKey(RetrievedChunk chunk) {
        if (chunk.getChunkId() != null) {
            return "c:" + chunk.getChunkId();
        }
        int contentHash = chunk.getContent() == null ? 0 : chunk.getContent().hashCode();
        return "d:" + chunk.getDocumentId() + ":p:" + chunk.getPage() + ":h:" + contentHash;
    }

    private static List<RetrievedChunk> copyAll(List<RetrievedChunk> source) {
        List<RetrievedChunk> copies = new ArrayList<>();
        if (source == null) {
            return copies;
        }
        for (RetrievedChunk chunk : source) {
            copies.add(copyOf(chunk));
        }
        return copies;
    }

    private static RetrievedChunk copyOf(RetrievedChunk source) {
        RetrievedChunk copy = new RetrievedChunk();
        if (source == null) {
            return copy;
        }
        copy.setChunkId(source.getChunkId());
        copy.setDocumentId(source.getDocumentId());
        copy.setContent(source.getContent());
        copy.setVectorScore(source.getVectorScore());
        copy.setBm25Score(source.getBm25Score());
        copy.setFinalScore(source.getFinalScore());
        copy.setPage(source.getPage());
        return copy;
    }

    /**
     * retrieve() 一次执行的中间快照。只由 {@link #diagnose(String, Long)} 填充。
     */
    public static class RecallTrace {
        public List<RetrievedChunk> vectorHits = new ArrayList<>();
        public List<RetrievedChunk> bm25Hits = new ArrayList<>();
        public List<RetrievedChunk> mergedByRrf = new ArrayList<>();
        public List<FilteredCandidate> filtered = new ArrayList<>();
        public List<RetrievedChunk> afterFilter = new ArrayList<>();
        public List<RetrievedChunk> finalTopK = new ArrayList<>();
        /** 本次 BM25 实际执行的 Redis 查询串；没有执行查询时为空 */
        public String bm25Query;
    }

    public static class FilteredCandidate {
        public RetrievedChunk chunk;
        public String reason;

        public FilteredCandidate(RetrievedChunk chunk, String reason) {
            this.chunk = chunk;
            this.reason = reason;
        }
    }
}
