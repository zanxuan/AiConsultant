package com.zx.consultant.common.config;

import com.zx.consultant.common.constant.VectorMetadataKeys;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private Integer redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * 手动接管 EmbeddingStore。
     * <p>
     * 注意：documentId/page/chunkId/chunkIndex 在 Redis JSON 里是数字，
     * 必须用 NUMERIC 字段；若用 metadataKeys（默认 TEXT）会导致整批文档索引失败（num_docs=0）。
     */
    @Bean
    public EmbeddingStore<TextSegment> redisEmbeddingStore() {
        Map<String, SchemaField> metadataConfig = new HashMap<>();
        metadataConfig.put(
                VectorMetadataKeys.DOCUMENT_ID,
                NumericField.of("$." + VectorMetadataKeys.DOCUMENT_ID).as(VectorMetadataKeys.DOCUMENT_ID));
        metadataConfig.put(
                VectorMetadataKeys.PAGE,
                NumericField.of("$." + VectorMetadataKeys.PAGE).as(VectorMetadataKeys.PAGE));
        metadataConfig.put(
                VectorMetadataKeys.CHUNK_ID,
                NumericField.of("$." + VectorMetadataKeys.CHUNK_ID).as(VectorMetadataKeys.CHUNK_ID));
        metadataConfig.put(
                VectorMetadataKeys.CHUNK_INDEX,
                NumericField.of("$." + VectorMetadataKeys.CHUNK_INDEX).as(VectorMetadataKeys.CHUNK_INDEX));
        metadataConfig.put(
                VectorMetadataKeys.KNOWLEDGE_ID,
                TextField.of("$." + VectorMetadataKeys.KNOWLEDGE_ID)
                        .as(VectorMetadataKeys.KNOWLEDGE_ID)
                        .weight(1.0));

        return RedisEmbeddingStore.builder()
                .host(redisHost)
                .port(redisPort)
                .password(redisPassword.isEmpty() ? null : redisPassword)
                .indexName("embedding-index")
                .dimension(1024)
                .metadataConfig(metadataConfig)
                .build();
    }
}
