package com.zx.consultant.rag.dto;


import lombok.Data;
import lombok.AllArgsConstructor;

/**
 * 引用来源数据传输对象，供前端渲染使用
 */
@Data
@AllArgsConstructor
public class CitationDTO {
    private Long documentId;
    private String documentName; // 如: Redis最佳实践.pdf
    private Integer page;
}