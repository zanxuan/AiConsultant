package com.zx.consultant.rag.service;

import java.util.List;

import com.zx.consultant.rag.entity.RetrievedChunk;

public interface RetrieverService {

    /**
     * 按知识库隔离检索
     * @param query 查询文本
     * @param knowledgeId 知识库 ID（必填，对应 Redis NUMERIC filter）
     */
    List<RetrievedChunk> retrieve(String query, Long knowledgeId);
}
