package com.zx.consultant.rag.eval;

/**
 * 可由检索快照确认的阶段事实。不是关键词不足、语义漂移等推测根因。
 */
public final class RetrievalMissStage {

    /** 至少一个期望文档进入评测文档 TopK */
    public static final String IN_EVAL_TOPK = "IN_EVAL_TOPK";

    /** 未进入向量召回，也未进入 BM25 召回 */
    public static final String NOT_RECALLED = "NOT_RECALLED";

    /** 进入融合候选后被过滤 */
    public static final String FILTERED = "FILTERED";

    /** 通过过滤，但未进入最终 Hybrid TopK */
    public static final String NOT_IN_FINAL_TOPK = "NOT_IN_FINAL_TOPK";

    /** 进入 Hybrid TopK，但文档去重后未进入评测 TopK */
    public static final String NOT_IN_EVAL_TOPK = "NOT_IN_EVAL_TOPK";

    /** 多个期望文档停在不同阶段 */
    public static final String MIXED = "MIXED";

    /** 用例没有期望文档 */
    public static final String NO_EXPECTED = "NO_EXPECTED";

    private RetrievalMissStage() {
    }
}
