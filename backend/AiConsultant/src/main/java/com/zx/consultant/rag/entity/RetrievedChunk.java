package com.zx.consultant.rag.entity;

import lombok.Data;

/**
 * 检索到的相关文档片段 (BM25 + Vector)
 */
@Data
public class RetrievedChunk {

    private Long chunkId;

    private Long documentId;

    private String content;

    private Double score;

    private Integer page;
}