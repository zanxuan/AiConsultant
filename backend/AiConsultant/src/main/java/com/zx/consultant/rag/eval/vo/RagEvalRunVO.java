package com.zx.consultant.rag.eval.vo;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 评测历史列表项。不含测试集和结果快照，避免分页把大 JSON 一并查出。
 */
@Data
@Schema(description = "RAG 评测运行记录")
public class RagEvalRunVO {

    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "知识库 ID")
    private Long knowledgeId;

    @Schema(description = "测试集来源：GOLDEN_SET / UPLOAD")
    private String datasetSource;

    @Schema(description = "文件名或来源名")
    private String datasetName;

    @Schema(description = "RUNNING / SUCCESS / FAILED")
    private String status;

    private Integer totalCases;

    private Double hitRate;

    private Double recall;

    private Double mrr;

    private Long avgLatencyMs;

    private Long p95LatencyMs;

    private Integer failedCount;

    @Schema(description = "整次评测耗时（ms）")
    private Long durationMs;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime endTime;
}
