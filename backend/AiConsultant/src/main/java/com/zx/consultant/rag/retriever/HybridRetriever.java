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
 * 融合后做简单相关性判断（无结果 / 最高向量分过低），由 RetrieveNode 短路友好回复，不进 LLM。
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

    /** 与向量通道一致的最低相似度；用于 Hybrid 后的最高分门槛 */
    @Value("${app.rag.retriever.min-score:0.75}")
    private Double minScore;

    public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
        List<RetrievedChunk> vectorHits = vectorRetriever.retrieve(query, knowledgeId);
        List<RetrievedChunk> bm25Hits = bm25Retriever.retrieve(query, knowledgeId);

        log.info("Hybrid 检索开始, knowledgeId={}, vectorHits={}, bm25Hits={}, rrfK={}, topK={}",
                knowledgeId, vectorHits.size(), bm25Hits.size(), rrfK, maxResults);

        List<RetrievedChunk> ranked = fuseWithRrf(vectorHits, bm25Hits);

        // 情况1 / 情况2 仅做简单判断与日志；友好回复由 RetrieveNode 短路，避免空资料进 LLM
        if (ranked.isEmpty()) {
            log.info("Hybrid 情况1: 无召回结果, knowledgeId={}", knowledgeId);
        } else if (isScoreTooLow(ranked)) {
            log.info("Hybrid 情况2: 最高分过低, knowledgeId={}, maxVectorScore={}, minScore={}",
                    knowledgeId, maxVectorScore(ranked), minScore);
        } else {
            log.info("Hybrid 召回通过, knowledgeId={}, size={}, maxVectorScore={}",
                    knowledgeId, ranked.size(), maxVectorScore(ranked));
        }
        return ranked;
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

    private double maxVectorScore(List<RetrievedChunk> docs) {
        return docs.stream()
                .map(RetrievedChunk::getVectorScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }

    /**
     * Reciprocal Rank Fusion：按各通道排名累加 1/(k+rank)，合并同 chunk 的通道分后截断 TopK。
     */
    private List<RetrievedChunk> fuseWithRrf(
            List<RetrievedChunk> vectorHits,
            List<RetrievedChunk> bm25Hits) {

        Map<String, RetrievedChunk> merged = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        accumulate(vectorHits, merged, rrfScores, true);
        accumulate(bm25Hits, merged, rrfScores, false);

        List<RetrievedChunk> ranked = new ArrayList<>(merged.values());
        for (RetrievedChunk chunk : ranked) {
            Double score = rrfScores.get(chunkKey(chunk));
            chunk.setFinalScore(score);
        }

        ranked.sort(Comparator.comparing(
                RetrievedChunk::getFinalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int limit = Math.min(maxResults, ranked.size());
        return new ArrayList<>(ranked.subList(0, limit));
    }

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
}
