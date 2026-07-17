package com.zx.consultant.document.service.impl;

import com.zx.consultant.document.dto.ParsedDocument;
import com.zx.consultant.document.entity.Chunk;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.document.enums.DocumentStatus;
import com.zx.consultant.document.mapper.DocumentMapper;
import com.zx.consultant.document.service.ChunkService;
import com.zx.consultant.document.service.DocumentProcessService;
import com.zx.consultant.document.service.EmbeddingService;
import com.zx.consultant.document.service.ParserService;
import com.zx.consultant.document.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档处理服务实现类
 * 负责统筹执行 AI 知识库核心流水线：解析 -> 切块 -> 向量化 -> 落盘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessServiceImpl implements DocumentProcessService {

    private final DocumentMapper documentMapper;
    private final ParserService parserService;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    /**
     * 异步处理文档（首次上传等场景）
     */
    @Async
    @Override
    public void processDocumentAsync(Long documentId) {
        processDocument(documentId);
    }

    /**
     * 异步清理文档的向量数据（删除文档等场景，可单独触发）
     */
    @Async
    @Override
    public void deleteVectorsAsync(Long documentId) {
        log.info("【AI 后台流水线】开始清理文档的向量数据, documentId: {}", documentId);
        try {
            vectorStoreService.deleteVectorsByDocumentId(documentId);
            log.info("【AI 后台流水线】向量清理完毕");
        } catch (Exception e) {
            log.error("【AI 后台流水线】向量清理失败, documentId: {}", documentId, e);
        }
    }

    /**
     * 异步重建索引：同一线程内严格串行，避免与 process / deleteVectors 并行竞态
     */
    @Async
    @Override
    public void reindexDocumentAsync(Long documentId) {
        log.info("【重建索引】开始, documentId: {}", documentId);
        try {
            // 1. 先清 MySQL 旧切块
            chunkService.deleteByDocumentId(documentId);
            log.info("【重建索引】旧切块已删除, documentId: {}", documentId);

            // 2. 再清向量库
            vectorStoreService.deleteVectorsByDocumentId(documentId);
            log.info("【重建索引】旧向量已删除, documentId: {}", documentId);

            // 3. 同步走处理流水线（本类内调用，不再二次 @Async）
            processDocument(documentId);
        } catch (Exception e) {
            log.error("【重建索引】清理阶段失败, documentId: {}", documentId, e);
            updateStatus(documentId, DocumentStatus.FAILED);
        }
    }

    /**
     * 同步核心流水线：解析 -> 切块 -> 向量化 -> 落盘
     * 供 processDocumentAsync / reindexDocumentAsync 复用，保证顺序可控
     */
    private void processDocument(Long documentId) {
        log.info("【AI 后台流水线】开始处理文档, documentId: {}", documentId);

        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            log.error("【AI 流水线异常】文档不存在, documentId: {}", documentId);
            return;
        }

        try {
            updateStatus(documentId, DocumentStatus.PARSING);

            ParsedDocument parsedDocument = parserService.parse(document);
            log.info("【AI 后台流水线】解析完成，获取正文长度: {}", parsedDocument.getContent().length());

            updateStatus(documentId, DocumentStatus.INDEXING);

            List<Chunk> chunks = chunkService.chunkAndSaveText(documentId, parsedDocument);
            log.info("【AI 后台流水线】文档切块完成，共切分出 {} 个块", chunks.size());

            List<List<Double>> embeddings = embeddingService.getEmbeddings(chunks);
            log.info("【AI 后台流水线】文档向量化完成");

            vectorStoreService.saveVectors(chunks, embeddings);
            log.info("【AI 后台流水线】向量数据成功落盘");

            document.setChunkCount(chunks.size());
            documentMapper.updateById(document);

            updateStatus(documentId, DocumentStatus.READY);
            log.info("【AI 后台流水线】文档处理全流程成功！documentId: {}", documentId);
        } catch (Exception e) {
            log.error("【AI 流水线崩溃】文档处理失败, documentId: {}", documentId, e);
            updateStatus(documentId, DocumentStatus.FAILED);
        }
    }

    private void updateStatus(Long documentId, DocumentStatus status) {
        Document updateEntity = new Document();
        updateEntity.setId(documentId);
        updateEntity.setStatus(status.name());
        updateEntity.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(updateEntity);
    }
}
