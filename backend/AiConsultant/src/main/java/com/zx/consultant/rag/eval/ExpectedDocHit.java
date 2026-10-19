package com.zx.consultant.rag.eval;

import lombok.Data;

/**
 * 单个期望文档在本次检索各阶段的命中情况。
 * 旧的 result_snapshot 没有该对象时反序列化为 null。
 */
@Data
public class ExpectedDocHit {

    private String expectedDocId;

    /** 向量通道中该文档首个 chunk 的排名；未召回为空 */
    private Integer vectorRank;

    /** BM25 中该文档首个 chunk 的排名；未召回为空 */
    private Integer bm25Rank;

    /** 过滤前 RRF 排名；未进入候选集合时为空，不根据扩展检索补写 */
    private Integer rrfRank;

    /** true 表示该文档的候选都被过滤；未进入候选集合时为空 */
    private Boolean filtered;

    private String filterReason;

    /** 最终 Hybrid TopK 中的 chunk 排名 */
    private Integer finalRank;

    /** 去重后文档在评测 TopK 中的排名 */
    private Integer evalDocRank;
}
