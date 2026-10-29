package com.zx.consultant.rag.eval;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

/**
 * 单条评测用例。旧 golden-set 与新测试集都先解析成这个对象。
 * <p>
 * {@code id} 仍是内部整数。新测试集的 {@code test-001} 由 {@link EvalCaseDeserializer} 拆开，
 * 数字写入 {@code id}，原始字符串写入 {@code sourceCaseId}。
 */
@Data
@JsonDeserialize(using = EvalCaseDeserializer.class)
public class EvalCase {

    /** 内部用例 ID。新格式 test-001 对应 1，供链路表的整型 case_id 继续使用 */
    private Integer id;

    /**
     * 新测试集的原始 ID，例如 test-001。
     * 旧 golden-set 没有该值，序列化时省略，避免改变原有快照字段。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sourceCaseId;

    /** 问题文本。旧字段 query 优先；只有新格式 question 时才写入这里 */
    private String query;

    /** 期望命中的文档 ID（与 RetrievedChunk.documentId 字符串形式对齐） */
    private List<String> expectedDocIds;

    /**
     * 标准 chunk ID，保持 JSON 里的原始字符串，不做数值转换。
     * 字段缺失时为 null，评测仍按文档判断；空列表表示没有有效标准 chunk。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> groundTruthChunkIds;
}
