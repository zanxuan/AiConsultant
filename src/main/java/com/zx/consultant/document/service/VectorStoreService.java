package com.zx.consultant.document.service;

import java.util.List;

import com.zx.consultant.document.entity.Chunk;

// 5. 向量库服务 (VectorStoreService)
public interface VectorStoreService {

    /**
     * 保存向量到 Redis
     * @param documentId 文档ID
     * @param chunks 文本块列表
     * @param embeddings 向量列表
     */     
    void saveVectors( List<Chunk> chunks, List<List<Double>> embeddings);
  
    /**
     * 从 Redis 删除指定文档的向量
     * @param documentId 文档ID
     */
    void deleteVectorsByDocumentId(Long documentId);
}