package com.zx.consultant.rag.eval.vo;

import com.zx.consultant.rag.eval.EvalResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发起评测的返回值。指标仍在 {@link EvalResult} 中，runId 只用于打开这一次的详情。
 * 历史落库失败时 runId 为空，计算结果照常返回。
 */
@Data
@Schema(description = "RAG 评测执行结果")
public class EvalRunExecuteVO {

    @Schema(description = "评测记录 ID；历史写入失败时为空")
    private Long runId;

    @Schema(description = "本次计算得到的评测结果")
    private EvalResult result;
}
