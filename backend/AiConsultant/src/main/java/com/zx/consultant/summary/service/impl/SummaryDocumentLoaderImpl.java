package com.zx.consultant.summary.service.impl;

import com.zx.consultant.document.entity.Chunk;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.document.enums.DocumentStatus;
import com.zx.consultant.document.service.ChunkService;
import com.zx.consultant.document.service.DocumentService;
import com.zx.consultant.document.service.ParserService;
import com.zx.consultant.summary.config.SummaryProperties;
import com.zx.consultant.summary.service.SummaryDocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 为总结流程加载 READY 文档：按 fileSize 区分短文档原文与长文档已入库 Chunk。
 */
@Slf4j
@Component
public class SummaryDocumentLoaderImpl implements SummaryDocumentLoader {

    private final DocumentService documentService;
    private final ChunkService chunkService;
    private final ParserService parserService;
    private final SummaryProperties summaryProperties;

    public SummaryDocumentLoaderImpl(DocumentService documentService,
                                     ChunkService chunkService,
                                     ParserService parserService,
                                     SummaryProperties summaryProperties) {
        this.documentService = documentService;
        this.chunkService = chunkService;
        this.parserService = parserService;
        this.summaryProperties = summaryProperties;
    }

    @Override
    public List<Document> listReadyDocuments(Long knowledgeId) {
        return documentService.lambdaQuery()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getStatus, DocumentStatus.READY.name())
                .orderByAsc(Document::getFileName)
                .list();
    }

    /**
     * 用库中的 fileSize（字节）判断是否为短文档，未记录大小时按长文档处理。
     */
    @Override
    public boolean isShortDocument(Document document) {
        Long fileSize = document == null ? null : document.getFileSize();
        return fileSize != null && fileSize <= summaryProperties.getShortTextThreshold();
    }

    /**
     * 加载文档原文，仅用于短文档直接总结，不重新切分。
     */
    @Override
    public String loadDocumentText(Document document) {
        try {
            var parsed = parserService.parse(document);
            return parsed == null || parsed.getContent() == null ? "" : parsed.getContent();
        } catch (Exception e) {
            log.warn("解析文档正文失败, documentId={}, fileName={}",
                    document.getId(), document.getFileName(), e);
            return "";
        }
    }

    /**
     * 按 chunkIndex 读取文档处理层已生成的切块正文，供长文档分段总结复用。
     */
    @Override
    public List<String> loadDocumentChunks(Document document) {
        List<Chunk> chunks = chunkService.lambdaQuery()
                .eq(Chunk::getDocumentId, document.getId())
                .orderByAsc(Chunk::getChunkIndex)
                .list();
        List<String> contents = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            log.warn("文档无已入库切块, documentId={}, fileName={}",
                    document.getId(), document.getFileName());
            return contents;
        }
        for (Chunk chunk : chunks) {
            if (chunk.getContent() != null && !chunk.getContent().isBlank()) {
                contents.add(chunk.getContent());
            }
        }
        return contents;
    }
}
