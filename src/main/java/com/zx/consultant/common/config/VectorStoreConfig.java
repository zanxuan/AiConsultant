package com.zx.consultant.common.config;

import com.zx.consultant.common.constant.VectorMetadataKeys;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private Integer redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * 手动接管 EmbeddingStore 的注册（适配 langchain4j 1.0.1-beta6 最新版）
     */
    @Bean
    public EmbeddingStore<TextSegment> redisEmbeddingStore() {
        return RedisEmbeddingStore.builder()
                .host(redisHost)
                .port(redisPort)
                .password(redisPassword.isEmpty() ? null : redisPassword)
                .indexName("consultant_knowledge_index")//索引名,相当于表名
                .dimension(1024) //维度,相当于字段
                // 统一注册所有的元数据 Key
                // 提前向 Redis 索引声明 “这个索引支持哪些元数据字段”
                .metadataKeys(Arrays.asList(
                        VectorMetadataKeys.DOCUMENT_ID,
                        VectorMetadataKeys.PAGE,
                        VectorMetadataKeys.CHUNK_ID,
                        VectorMetadataKeys.CHUNK_INDEX,
                        VectorMetadataKeys.KNOWLEDGE_ID
                ))
                .build();
    }


   
}