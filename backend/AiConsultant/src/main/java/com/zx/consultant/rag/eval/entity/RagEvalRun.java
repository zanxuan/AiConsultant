package com.zx.consultant.rag.eval.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * RAG 评测运行记录，对应表 rag_eval_run。
 */
@Data
@TableName("rag_eval_run")
public class RagEvalRun {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 发起用户；命令行 --eval 无登录上下文时为空 */
    private Long userId;

    private Long knowledgeId;

    /** 评测使用的向量模型，对应 langchain4j embedding-model */
    private String modelName;

    /** 创建记录时的知识库名称快照 */
    private String knowledgeName;

    /** {@link com.zx.consultant.rag.eval.RagEvalDatasetSource#name()} */
    private String datasetSource;

    private String datasetName;

    /** 实际参与计算的 EvalCase JSON */
    private String datasetSnapshot;

    /** {@link com.zx.consultant.rag.eval.RagEvalRunStatus#name()} */
    private String status;

    private Integer totalCases;

    private Double hitRate;

    private Double recallAtK;

    private Double mrr;

    private Long avgLatencyMs;

    private Long p95LatencyMs;

    private Integer failedCount;

    /** 整次评测墙钟耗时，不同于单次检索的 avg/p95 */
    private Long durationMs;

    private String configSnapshot;

    /** 完整 EvalResult JSON */
    private String resultSnapshot;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private LocalDateTime endTime;
}
