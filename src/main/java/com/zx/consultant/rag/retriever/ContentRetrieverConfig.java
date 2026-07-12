package com.zx.consultant.rag.retriever;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentRetrieverConfig {

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private RedisEmbeddingStore redisEmbeddingStore;

    @Bean
    public ContentRetriever contentRetriever() {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(redisEmbeddingStore)
                .minScore(0.5)
                .maxResults(3)
                .embeddingModel(embeddingModel)
                .build();
    }
}
