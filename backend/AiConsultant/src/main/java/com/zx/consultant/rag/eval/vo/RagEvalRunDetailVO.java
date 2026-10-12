package com.zx.consultant.rag.eval.vo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.zx.consultant.rag.eval.EvalCase;
import com.zx.consultant.rag.eval.EvalResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评测历史详情。cases 与 result 来自落库快照，不再重新检索。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "RAG 评测运行详情")
public class RagEvalRunDetailVO extends RagEvalRunVO {

    @Schema(description = "当时的 topK 与 retriever 配置")
    private Map<String, Object> configSnapshot = new LinkedHashMap<>();

    @Schema(description = "实际参与计算的用例")
    private List<EvalCase> cases = new ArrayList<>();

    @Schema(description = "完整评测结果；失败且尚未算出结果时为空")
    private EvalResult result;
}
