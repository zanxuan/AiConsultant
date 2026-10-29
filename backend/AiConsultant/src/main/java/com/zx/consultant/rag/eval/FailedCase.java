package com.zx.consultant.rag.eval;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class FailedCase {

    private Integer id;

    private String query;

    private List<String> expectedDocIds;

    private List<String> retrievedDocIds;

    /** 去重后的标准 chunk。文档级失败或旧快照没有该字段时为 null */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> groundTruthChunkIds;

    /** 参与打分的前 TOP_K 个 chunk ID，空 ID 以 null 占位 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> retrievedChunkIds;

    /** 标准 chunk 为空时的排查说明 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String scoreNote;

    private Double topScore;

    /** 该失败 case 的 HybridRetriever 检索耗时（ms） */
    private Long latencyMs;
}
