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
}
