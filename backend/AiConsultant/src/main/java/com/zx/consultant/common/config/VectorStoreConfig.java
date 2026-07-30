package com.zx.consultant.common.config;

import com.zx.consultant.common.constant.VectorMetadataKeys;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;

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

    @Value("${app.rag.redis.index-name:embedding-index}")
    private String indexName;

    /**
     * 手动接管 EmbeddingStore。
     * <p>
     * 注意：documentId/knowledgeId/page/chunkId/chunkIndex 在 Redis JSON 里是数字，
     * 必须用 NUMERIC 字段；若用 metadataKeys（默认 TEXT）会导致整批文档索引失败（num_docs=0）。
     * <p>
     * NUMERIC 过滤不要用 isEqualTo（会生成非法的 {@code @field:[id]}），
     * 应使用 gte + lte 拼出 {@code @field:[id id]}。
     */
    @Bean
    public EmbeddingStore<TextSegment> redisEmbeddingStore() {
        Map<String, SchemaField> metadataConfig = new HashMap<>();
        metadataConfig.put(
                VectorMetadataKeys.DOCUMENT_ID,
                NumericField.of("$." + VectorMetadataKeys.DOCUMENT_ID).as(VectorMetadataKeys.DOCUMENT_ID));
        metadataConfig.put(
                VectorMetadataKeys.KNOWLEDGE_ID,
                NumericField.of("$." + VectorMetadataKeys.KNOWLEDGE_ID).as(VectorMetadataKeys.KNOWLEDGE_ID));
        metadataConfig.put(
                VectorMetadataKeys.PAGE,
                NumericField.of("$." + VectorMetadataKeys.PAGE).as(VectorMetadataKeys.PAGE));
        metadataConfig.put(
                VectorMetadataKeys.CHUNK_ID,
                NumericField.of("$." + VectorMetadataKeys.CHUNK_ID).as(VectorMetadataKeys.CHUNK_ID));
        metadataConfig.put(
                VectorMetadataKeys.CHUNK_INDEX,
                NumericField.of("$." + VectorMetadataKeys.CHUNK_INDEX).as(VectorMetadataKeys.CHUNK_INDEX));

        // langchain4j-community-redis 1.0.1-beta6: password is ignored unless user is non-null
        // (it branches on user!=null to choose JedisPooled with auth).
        boolean hasPassword = redisPassword != null && !redisPassword.isEmpty();
        return RedisEmbeddingStore.builder()
                .host(redisHost)
                .port(redisPort)
                .user(hasPassword ? "default" : null)
                .password(hasPassword ? redisPassword : null)
                .indexName(indexName)
                .dimension(1024)
                .metadataConfig(metadataConfig)
                .build();
    }
}
