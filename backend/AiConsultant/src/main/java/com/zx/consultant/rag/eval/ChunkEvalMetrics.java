package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import com.zx.consultant.rag.entity.RetrievedChunk;

/**
 * Chunk 级 Hit / Recall / MRR。只根据已经检索好的列表打分，不发起检索。
 * <p>
 * 标准 ID 去重后按集合计算。检索名次保留空 chunk ID 的原始位置，第 K 名之后不参与打分。
 */
final class ChunkEvalMetrics {

    /** 显式空标准集合时的排查说明。这种情况不回退到文档判断 */
    static final String EMPTY_GROUND_TRUTH_NOTE = "标准 chunk 集合为空，按未命中计，未回退到文档判断";

    private ChunkEvalMetrics() {
    }

    /**
     * @param groundTruthChunkIds 调用方已确认不是 null。空列表和清理后没有有效 ID 都按未命中
     * @param retrieved 最终检索结果，可以长于 topK
     * @param topK 只评估前 topK 条
     */
    static ChunkMatch match(List<String> groundTruthChunkIds, List<RetrievedChunk> retrieved, int topK) {
        LinkedHashSet<String> groundTruth = normalizeGroundTruth(groundTruthChunkIds);
        List<String> retrievedIds = topChunkIds(retrieved, topK);
        if (groundTruth.isEmpty()) {
            return new ChunkMatch(false, 0.0d, 0.0d, List.copyOf(groundTruth), retrievedIds,
                    EMPTY_GROUND_TRUTH_NOTE);
        }

        LinkedHashSet<String> matched = new LinkedHashSet<>();
        int firstRank = 0;
        for (int i = 0; i < retrievedIds.size(); i++) {
            String chunkId = retrievedIds.get(i);
            if (chunkId != null && groundTruth.contains(chunkId)) {
                matched.add(chunkId);
                if (firstRank == 0) {
                    firstRank = i + 1;
                }
            }
        }
        // Hit 看是否有交集，Recall 看覆盖比例，二者分开计算
        boolean hit = !matched.isEmpty();
        double recall = (double) matched.size() / groundTruth.size();
        double reciprocalRank = firstRank == 0 ? 0.0d : 1.0d / firstRank;
        return new ChunkMatch(hit, recall, reciprocalRank, List.copyOf(groundTruth), retrievedIds, null);
    }

    /** 去空白、去掉空串，重复 ID 只保留第一次出现的顺序 */
    private static LinkedHashSet<String> normalizeGroundTruth(List<String> groundTruthChunkIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (groundTruthChunkIds == null) {
            return ids;
        }
        for (String raw : groundTruthChunkIds) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 前 topK 个位置原样保留。chunkId 为空时该位置记为 null，后面的结果不前移。
     * Long 用十进制字符串比较，不经过浮点数。
     */
    private static List<String> topChunkIds(List<RetrievedChunk> retrieved, int topK) {
        if (retrieved == null || retrieved.isEmpty() || topK <= 0) {
            return List.of();
        }
        int end = Math.min(topK, retrieved.size());
        List<String> ids = new ArrayList<>(end);
        for (int i = 0; i < end; i++) {
            ids.add(chunkId(retrieved.get(i)));
        }
        return Collections.unmodifiableList(ids);
    }

    private static String chunkId(RetrievedChunk chunk) {
        if (chunk == null || chunk.getChunkId() == null) {
            return null;
        }
        String id = String.valueOf(chunk.getChunkId()).trim();
        return id.isEmpty() ? null : id;
    }

    record ChunkMatch(
            boolean hit,
            double recall,
            double reciprocalRank,
            List<String> groundTruthChunkIds,
            List<String> retrievedChunkIds,
            String scoreNote) {
    }
}
