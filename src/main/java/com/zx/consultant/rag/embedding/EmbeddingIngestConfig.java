package com.zx.consultant.rag.embedding;

import com.zx.consultant.document.ContentDocumentLoader;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class EmbeddingIngestConfig {

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private RedisEmbeddingStore redisEmbeddingStore;
    @Autowired
    private ContentDocumentLoader contentDocumentLoader;

    /**
     * 启动时执行：加载 content 文档 → 切分 → 向量化 → 写入 Redis。
     * 需要入库时取消下一行 @Bean 注释。
     */
    //@Bean
    public EmbeddingStore embeddingStore() {
        List<Document> documents = contentDocumentLoader.load();

        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(redisEmbeddingStore)
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .build();
        ingestor.ingest(documents);
        return redisEmbeddingStore;
    }
}
