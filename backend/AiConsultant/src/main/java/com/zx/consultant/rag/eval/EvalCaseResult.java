package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** 前 TOP_K 是否命中。chunk 模式看 chunk 交集，旧格式看文档 */
    private boolean hit;

    /** chunk 模式是覆盖比例；旧格式仍是命中 1.0、未命中 0.0 */
    private double recall;

    /** 第一个命中项的排名倒数。chunk 模式按 chunk 名次，未命中为 0 */
    private double mrr;

    /** 去重后的标准 chunk。旧格式或旧快照没有该字段时为 null */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> groundTruthChunkIds;

    /**
     * 参与打分的前 TOP_K 个 chunk ID，空 ID 以 null 占位。
     * 不是文档 ID，旧快照没有该字段时为 null。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> retrievedChunkIds;

    /** 标准 chunk 为空等可确认的打分说明。正常命中时为 null */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String scoreNote;

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
