package com.zx.consultant.rag.eval.vo;

import java.util.ArrayList;
import java.util.List;
import com.zx.consultant.rag.eval.ExpectedDocHit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 单个用例的历史检索快照。列表由已保存明细组装，不重新检索。
 */
@Data
@Schema(description = "评测用例检索链路")
public class RagEvalRetrievalTraceVO {

    private Long runId;

    private Integer caseId;

    /** false 表示这次评测没有保存检索链路 */
    private boolean saved;

    private Integer vectorHitCount;

    private Integer bm25HitCount;

    private String bm25Query;

    private String missStage;

    private List<ExpectedDocHit> expectedHits = new ArrayList<>();

    private List<RetrievalTraceChunkVO> vectorHits = new ArrayList<>();

    private List<RetrievalTraceChunkVO> bm25Hits = new ArrayList<>();

    private List<RetrievalTraceChunkVO> rrfHits = new ArrayList<>();

    private List<RetrievalTraceChunkVO> afterFilter = new ArrayList<>();

    private List<RetrievalTraceChunkVO> finalTopK = new ArrayList<>();

    private List<RetrievalEvalDocVO> evalDocs = new ArrayList<>();

    @Data
    @Schema(description = "检索链路中的一个候选 chunk")
    public static class RetrievalTraceChunkVO {

        private Long chunkId;

        private Long documentId;

        private String documentName;

        private Integer page;

        /** 查询时按 chunk 表补全，不来自检索明细表 */
        private String content;

        private Integer vectorRank;

        private Double vectorScore;

        private Integer bm25Rank;

        private Double bm25Score;

        private Integer rrfRank;

        private Double rrfScore;

        private Boolean filtered;

        private String filterReason;

        private Integer afterFilterRank;

        private Integer finalRank;

        private Integer evalDocRank;
    }

    @Data
    @Schema(description = "去重后的评测文档排名")
    public static class RetrievalEvalDocVO {

        private Integer rank;

        private Long documentId;

        private String documentName;

        private Boolean expected;
    }
}
