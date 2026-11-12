package com.zx.consultant.rag.eval;

import lombok.Data;

/**
 * 单题生成评测结果。某项裁判失败时对应分数为空，答案仍然保留。
 */
@Data
public class GenerationEvalResult {

    /** 去掉 cite 标记后的答案 */
    private String answer;

    /** 答案中的事实能否在本次提示词资料里找到依据。未打分时为空 */
    private Double faithfulness;

    private String faithfulnessReason;

    /** 答案是否覆盖标准答案中的事实。没有标准答案或未打分时为空 */
    private Double correctness;

    private String correctnessReason;

    private String generationModel;

    /** 打忠实度的模型。未打分时也记录，方便和正确性裁判对照 */
    private String faithfulnessJudgeModel;

    /** 打正确性的模型 */
    private String correctnessJudgeModel;
}
