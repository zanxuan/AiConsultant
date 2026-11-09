package com.zx.consultant.rag.entity;

import lombok.Data;

/**
 * 检索到的相关文档片段 (BM25 + Vector)
 * <p>
 * 注意：vectorScore（约 0~1）与 bm25Score（无界正数）不在同一量纲，
 * 禁止互相直接比较；Hybrid 融合后写入 finalScore。
 */
@Data
public class RetrievedChunk {

    private Long chunkId;

    private Long documentId;

    private String content;

    /** @deprecated 历史兼容字段，请使用 vectorScore / bm25Score / finalScore */
    private Double score;

    /** 向量相似度分（通常约 0~1） */
    private Double vectorScore;

    /** RediSearch BM25 分（无界，不可与 vectorScore 直接比较） */
    private Double bm25Score;

    /** Hybrid / RRF 融合后的最终分。重排序不改写这个分，避免和 RRF 量纲混在一起 */
    private Double finalScore;

    /**
     * 交叉编码器相关性分，只在重排序开启且调用成功时有值。
     * 用来决定返回顺序，不参与阈值过滤。
     */
    private Double rerankScore;

    private Integer page;
}
