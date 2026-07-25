package com.zx.consultant.rag.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用来源数据传输对象，供前端渲染使用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitationDTO {
    private Long documentId;
    private String documentName; // 如: Redis最佳实践.pdf
    private Integer page;
    /** 被引用的原文片段，便于前端展开查看 */
    private String content;
}
