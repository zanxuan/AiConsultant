package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class EvalResult {

    private int total;

    private double hitRate;

    private double recall;

    private double mrr;

    private List<FailedCase> failedCases = new ArrayList<>();

    /** 全部 case 的检索明细（含 Passed），不改变汇总指标计算 */
    private List<EvalCaseResult> cases = new ArrayList<>();

    /** 全部 case 检索耗时的算术平均（ms）；无 case 时为 null */
    private Long avgLatencyMs;

    /** 全部 case 检索耗时的 P95（ms）；无 case 时为 null */
    private Long p95LatencyMs;
}
