package com.zx.consultant.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.document.dto.ParsedDocument;
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

        // 1. 基于防腐层对象进行判空
        if (parsedDocument == null || parsedDocument.getContent() == null || parsedDocument.getContent().trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 转换：抽取出的 Metadata 构建逻辑
        Metadata lcMetadata = buildMetadata(parsedDocument);

        // 构建出 LangChain4j 兼容的 Document
        Document document = Document.from(parsedDocument.getContent(), lcMetadata);

        // 3. 初始化切块器
        DocumentSplitter splitter = DocumentSplitters.recursive(MAX_SEGMENT_TOKENS, MAX_OVERLAP_TOKENS);

        // 4. 执行切块：此时 splitter 会自动把 lcMetadata 继承给下面切分出的所有 segments
        List<TextSegment> segments = splitter.split(document);

        List<Chunk> chunkList = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            
            Chunk chunk = new Chunk();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(i + 1);
            chunk.setContent(segment.text());
            
            // TODO 5. 精准获取 Token 数量 (目前注释掉，后续可按需放开或改名)
             chunk.setTokenSize(segment.text().length());
            
            // 6. 获取页码
            Integer page = null;
            if (segment.metadata().containsKey("page")) {
                try {
                    // 如果原先传入的是 Integer，正常获取
                    page = segment.metadata().getInteger("page");
                } catch (NumberFormatException e){
                    // 兼容处理：如果组装元数据时被转成了 String，这里兜底解析
                    page = Integer.parseInt(segment.metadata().getString("page"));
                }
            }
            
            chunk.setPage(page != null ? page : 1); // 容错处理，如果没有页码默认设为 1
            
            chunkList.add(chunk);
        }

        // 7. 批量保存
        this.saveBatch(chunkList);
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

        // 构造 Lambda 查询条件：WHERE document_id = ?
        LambdaQueryWrapper<Chunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chunk::getDocumentId, documentId);

        // 调用 ServiceImpl 内置的 remove 方法执行批量删除
        boolean removed = this.remove(wrapper);
        
        log.info("MySQL 切块数据清理完毕，文档ID: {}, 是否有数据被删除: {}", documentId, removed);
       
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
        
        // 增加一点容错：如果 getMetadata 本身为空，直接返回空的 Metadata
        if (parsedDocument == null || parsedDocument.getMetadata() == null) {
            return lcMetadata;
        }

        for (Map.Entry<String, Object> entry : parsedDocument.getMetadata().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;
            
            // LangChain4j 的 Metadata 支持特定数据类型的 put，这里做类型分发
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