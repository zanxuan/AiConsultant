package com.zx.consultant.rag.eval.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 一次评测用例中进入 RRF 候选集合的 chunk。不保存正文。
 */
@Data
@TableName("rag_eval_retrieval_trace")
public class RagEvalRetrievalTrace {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long runId;

    private Integer caseId;

    private Long chunkId;

    private Long documentId;

    private Integer page;

    /** 向量通道排名，从 1 开始；只出现在 BM25 时为空 */
    private Integer vectorRank;

    private Double vectorScore;

    /** BM25 排名，从 1 开始；只出现在向量通道时为空 */
    private Integer bm25Rank;

    private Double bm25Score;

    /** 过滤前融合排名 */
    private Integer rrfRank;

    private Double rrfScore;

    /** 1 表示该候选在截断前被过滤 */
    private Integer filtered;

    private String filterReason;

    /** 过滤后、截断前排名；被过滤或未进入该阶段时为空 */
    private Integer afterFilterRank;

    /** 最终 Hybrid TopK 中的排名 */
    private Integer finalRank;

    /** 文档去重后，在评测 TopK 中的排名 */
    private Integer evalDocRank;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
