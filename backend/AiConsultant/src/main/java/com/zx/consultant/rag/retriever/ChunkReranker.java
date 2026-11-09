package com.zx.consultant.rag.retriever;

import java.util.List;
import com.zx.consultant.rag.entity.RetrievedChunk;

/**
 * 对已经融合、截断后的候选打相关性分。
 * 分数与入参下标对齐。返回 null 分数时，调用方保留原来的 RRF 顺序。
 */
public interface ChunkReranker {

    RerankScores score(String query, List<RetrievedChunk> chunks);

    /**
     * @param scores 与候选等长、下标对齐的相关性分；无法完成时为 null
     * @param latencyMs 本次调用耗时，未发出请求时也可以为 0
     * @param fallbackReason 失败原因；成功时为 null
     */
    record RerankScores(double[] scores, long latencyMs, String fallbackReason) {

        public boolean applied() {
            return scores != null && fallbackReason == null;
        }
    }
}
