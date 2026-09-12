package com.zx.consultant.summary.service;

import com.zx.consultant.document.entity.Document;

import java.util.List;

/**
 * 为总结流程加载 READY 文档：按 fileSize 区分短文档原文与长文档已入库 Chunk。
 */
public interface SummaryDocumentLoader {

    /**
     * 按文件名升序列出知识库中已就绪文档。
     */
    List<Document> listReadyDocuments(Long knowledgeId);

    /**
     * 用库中的 fileSize（字节）判断是否为短文档，未记录大小时按长文档处理。
     */
    boolean isShortDocument(Document document);

    /**
     * 加载文档原文，仅用于短文档直接总结，不重新切分。
     */
    String loadDocumentText(Document document);

    /**
     * 按 chunkIndex 读取文档处理层已生成的切块正文，供长文档分段总结复用。
     */
    List<String> loadDocumentChunks(Document document);
}
