package com.zx.consultant.document.service;

public interface DocumentProcessService {

    /**
     * 异步触发文档处理全流程（解析 -> 切块 -> 向量化 -> 存储）
     * @param documentId 待处理的文档 ID
     */
    void processDocumentAsync(Long documentId);

    /**
     * 异步清理向量数据库中的数据
     * @param documentId 待清理的文档 ID
     */
    void deleteVectorsAsync(Long documentId);

    /**
     * 异步重建索引：在同一异步任务内串行执行
     * 删除旧切块 -> 删除旧向量 -> 重新解析入库
     * （禁止拆成多个独立 @Async，否则会竞态）
     *
     * @param documentId 待重建的文档 ID
     */
    void reindexDocumentAsync(Long documentId);
}