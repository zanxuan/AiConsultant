package com.zx.consultant.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.document.dto.ParsedDocument;
import com.zx.consultant.document.dto.ParsedPage;
import com.zx.consultant.document.entity.Chunk;
import com.zx.consultant.document.mapper.ChunkMapper;
import com.zx.consultant.document.service.ChunkService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChunkServiceImpl extends ServiceImpl<ChunkMapper, Chunk> implements ChunkService {

    // 最大 Token 数 (这里目前退化为纯字符数切分)
    private static final int MAX_SEGMENT_TOKENS = 500;
    private static final int MAX_OVERLAP_TOKENS = 50;

    /**
     * 文本切块并保存
     * @param documentId 文档ID
     * @param parsedDocument 解析后的文档对象
     * @return 切块列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Chunk> chunkAndSaveText(Long documentId, ParsedDocument parsedDocument) {
        log.info("开始执行文本切块，文档ID: {}", documentId);

        if (parsedDocument == null) {
            return new ArrayList<>();
        }

        List<ParsedPage> pages = resolvePages(parsedDocument);
        if (pages.isEmpty()) {
            return new ArrayList<>();
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(MAX_SEGMENT_TOKENS, MAX_OVERLAP_TOKENS);

        List<Chunk> chunkList = new ArrayList<>();
        int chunkIndex = 1;

        for (ParsedPage page : pages) {
            String pageText = page.getContent();
            if (pageText == null || pageText.trim().isEmpty()) {
                continue;
            }

            // 每页独立切块，并把真实页码写入 metadata，再继承到 segment
            Metadata lcMetadata = buildMetadata(parsedDocument);
            lcMetadata.put("page", page.getPageNumber());

            Document document = Document.from(pageText, lcMetadata);
            List<TextSegment> segments = splitter.split(document);

            for (TextSegment segment : segments) {
                Chunk chunk = new Chunk();
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(chunkIndex++);
                chunk.setContent(segment.text());
                // TODO(V2): 精准获取 Token 数量 (目前用字符数代替)
                chunk.setTokenSize(segment.text().length());
                chunk.setPage(page.getPageNumber());
                chunkList.add(chunk);
            }
        }

        if (!chunkList.isEmpty()) {
            this.saveBatch(chunkList);
        }
        log.info("切片入库完成，成功保存 {} 条记录", chunkList.size());

        return chunkList;
    }


    /**
     * 删除数据库中的chunk元数据
     * @param documentId
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        log.info("开始从 MySQL 删除文档关联的切块数据，文档ID: {}", documentId);
        
        if (documentId == null) {
            log.warn("传入的 documentId 为空，放弃删除操作");
            return;
        }

        LambdaQueryWrapper<Chunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chunk::getDocumentId, documentId);

        boolean removed = this.remove(wrapper);
        
        log.info("MySQL 切块数据清理完毕，文档ID: {}, 是否有数据被删除: {}", documentId, removed);
       
    }

    /**
     * 优先使用按页结构；若无 pages，则把全文视为第 1 页（兼容旧调用）
     */
    private List<ParsedPage> resolvePages(ParsedDocument parsedDocument) {
        if (parsedDocument.getPages() != null && !parsedDocument.getPages().isEmpty()) {
            return parsedDocument.getPages();
        }
        List<ParsedPage> fallback = new ArrayList<>();
        if (parsedDocument.getContent() != null && !parsedDocument.getContent().trim().isEmpty()) {
            fallback.add(new ParsedPage(1, parsedDocument.getContent()));
        }
        return fallback;
    }

    /**
     * 构建 LangChain4j 的 Metadata
     * 供 Embedding, Retriever, Workflow 等模块复用
     * 用于描述这“特定一群数据”的数据
     *
     * @param parsedDocument 业务层解析出的文档对象
     * @return 兼容 LangChain4j 格式的 Metadata
     */
    private Metadata buildMetadata(ParsedDocument parsedDocument) {
        Metadata lcMetadata = new Metadata();
        
        if (parsedDocument == null || parsedDocument.getMetadata() == null) {
            return lcMetadata;
        }

        for (Map.Entry<String, Object> entry : parsedDocument.getMetadata().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;
            
            if (value instanceof Integer) {
                lcMetadata.put(key, (Integer) value);
            } else if (value instanceof Long) {
                lcMetadata.put(key, (Long) value);
            } else if (value instanceof Float) {
                lcMetadata.put(key, (Float) value);
            } else if (value instanceof Double) {
                lcMetadata.put(key, (Double) value);
            } else {
                lcMetadata.put(key, value.toString());
            }
        }
        
        return lcMetadata;
    }
}
