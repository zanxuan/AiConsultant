package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 单条评测明细（Passed / Failed 都保留），供前端 Case 详情使用。
 */
@Data
public class EvalCaseResult {

    private Integer id;

    private String query;

    private List<String> expectedDocIds = new ArrayList<>();

    private List<String> retrievedDocIds = new ArrayList<>();

    private Double topScore;

    private Long latencyMs;

    /** 与 isHit() 一致：TopK 是否命中任意 expected 文档 */
    private boolean hit;

    /** 与现有 Recall 计算一致：命中 1.0，未命中 0.0 */
    private double recall;

    /** 该 case 的 reciprocalRank，未命中为 0 */
    private double mrr;

    /** 向量通道实际返回条数。旧快照没有该字段时为 null */
    private Integer vectorHitCount;

    /** BM25 通道实际返回条数。旧快照没有该字段时为 null */
    private Integer bm25HitCount;

    /** 本次实际执行的 RediSearch 查询串 */
    private String bm25Query;

    /** 每个期望文档的阶段命中，不合并成一个布尔值 */
    private List<ExpectedDocHit> expectedHits;

    /**
     * 可由本次检索快照确认的阶段。
     * 只记录事实，不写关键词不足、语义漂移等推测根因。
     */
    private String missStage;
}
