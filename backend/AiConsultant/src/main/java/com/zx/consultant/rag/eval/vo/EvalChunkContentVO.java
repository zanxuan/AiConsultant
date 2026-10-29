package com.zx.consultant.rag.eval.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 按 chunk 主键读出的原文。字段与切块表的 id、content 对应，chunkId 用字符串避免雪花 ID 丢精度。
 */
@Data
@Schema(description = "评测标准 Chunk 原文")
public class EvalChunkContentVO {

    @Schema(description = "Chunk ID，与标准答案中的字符串一致")
    private String chunkId;

    /** 切块表中的原文。查不到或不属于本次评测知识库时为空 */
    @Schema(description = "Chunk 原文；无法读取时为空")
    private String content;
}
