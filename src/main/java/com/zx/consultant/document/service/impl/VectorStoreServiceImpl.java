package com.zx.consultant.document.service.impl;

import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.document.entity.Chunk;
import com.zx.consultant.document.service.VectorStoreService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.zx.consultant.common.constant.VectorMetadataKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    // 依赖注入：由 Spring Boot 提供的 LangChain4j 向量库操作 Bean
    private final EmbeddingStore<TextSegment> embeddingStore;


    /**
     * 保存向量到 Redis
     * @param chunks 包含了文本、页码、文档ID等元数据的实体列表
     * @param embeddings 对应的向量列表
     */
    @Override
    public void saveVectors(List<Chunk> chunks, List<List<Double>> embeddings) {
        log.info("开始将文档的向量存入 Redis, 待处理切块数量: {}", chunks == null ? 0 : chunks.size());

        // 1. 数据校验
        if (chunks == null || embeddings == null || chunks.isEmpty()) {
            log.warn("切块或向量数据为空，放弃保存。");
            return;
        }
        if (chunks.size() != embeddings.size()) {
            log.error("严重错误：文本切块的数量 ({}) 与向量的数量 ({}) 不一致！", chunks.size(), embeddings.size());
            throw new IllegalArgumentException("切块数量与向量数量不匹配");
        }

        List<String> ids = new ArrayList<>(chunks.size());
        List<TextSegment> segments = new ArrayList<>(chunks.size());
        List<Embedding> langchainEmbeddings = new ArrayList<>(embeddings.size());

        // 2. 将纯业务数据转化为 AI 框架的底层对象
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            
            // 提取 MySQL 中已经生成的 vectorId，确保 MySQL 和 Redis 里的 ID 完全一致
            if (chunk.getVectorId() == null || chunk.getVectorId().isBlank()) {
                throw new IllegalArgumentException("Chunk 缺少 vectorId");
            }
            ids.add(chunk.getVectorId());

            // 【核心升级】：组装更丰富的 Metadata
            // 现在我们可以把实体里的所有有用信息都塞进 Redis，大模型回答时可以直接引用这些元数据
            Metadata metadata = new Metadata();
            metadata.put(VectorMetadataKeys.DOCUMENT_ID, chunk.getDocumentId()); // 用于精确删除或按文档过滤
            
            if (chunk.getPage() != null) {
                metadata.put(VectorMetadataKeys.PAGE, chunk.getPage()); // 溯源：记录这是在 PDF 的哪一页
            }
            if (chunk.getChunkIndex() != null) {
                metadata.put(VectorMetadataKeys.CHUNK_INDEX, chunk.getChunkIndex()); // 记录段落顺序
            }
            
            // 使用 Chunk 里的真实文本内容来构建 TextSegment
            segments.add(TextSegment.from(chunk.getContent(), metadata));

            // 将 List<Double> 降级转回 float[] 数组
            List<Double> doubleList = embeddings.get(i);
            float[] floatArray = new float[doubleList.size()];
            for (int j = 0; j < doubleList.size(); j++) {
                floatArray[j] = doubleList.get(j).floatValue();
            }
            langchainEmbeddings.add(new Embedding(floatArray));
        }

        // 3. 批量存入 Redis
        try {
            // 【核心升级】：使用带有 ID 参数的 addAll 方法，强制向量库使用我们在上一步生成的 UUID
            embeddingStore.addAll(ids, langchainEmbeddings, segments);
            log.info("文档向量成功存入 Redis! 数量: {}", chunks.size());
        } catch (Exception e) {
            log.error("存入 Redis 向量库时发生异常: ", e);
            throw new RuntimeException("向量数据落盘失败", e);
        }
    }

    /**
     * 从 Redis 删除指定文档的向量
     * @param documentId 文档ID
     */
    @Override
    public void deleteVectorsByDocumentId(Long documentId) {
        log.info("开始从 Redis 清理旧的文档向量, documentId: {}", documentId);

        try {
            // 利用 LangChain4j 的 Metadata 过滤器功能，精准删除带有这个 documentId 标签的所有切块
            Filter filter = metadataKey(VectorMetadataKeys.DOCUMENT_ID).isEqualTo(documentId);
            
            // 执行条件删除
            embeddingStore.removeAll(filter);
            
            log.info("旧文档向量清理完毕! documentId: {}", documentId);
        } catch (UnsupportedOperationException e) {
            // 容错：有些较老的向量库版本可能还不支持 removeAll 方法
            log.warn("当前的 EmbeddingStore 底层实现暂时不支持按条件删除，请注意脏数据清理问题", e);
        } catch (Exception e) {
            log.error("从 Redis 删除向量数据时发生未知异常: ", e);
            // 【核心修改】：抛出异常，通知上层调用者（如 DocumentService）进行 MySQL 回滚
              throw new BaseException("清理向量库数据失败，请检查 Redis 连接");
        }
    }
}