package com.zx.consultant.rag.retriever;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.common.constant.VectorMetadataKeys;
import com.zx.consultant.rag.entity.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

/**
 * BM25 关键词检索器
 * <p>
 * 复用向量入库时的 Redis Stack 索引（{@code app.rag.redis.index-name}）：
 * {@code $.text} 已建为 TEXT 字段，RediSearch 默认按 BM25 打分。
 * 专攻专有名词 / 编号 / API 名 / 错误码等关键词召回，不做向量检索。
 */
@Slf4j
@Component
public class BM25Retriever {

    /** RedisEmbeddingStore 默认标量文本字段名 */
    private static final String TEXT_FIELD = "text";

    private static final String JSON_ROOT = "$";

    private final JedisPooled jedis;
    private final ObjectMapper objectMapper;

    /** 与 VectorStoreConfig / EmbeddingStore 共用，避免环境切换时漏改 */
    @Value("${app.rag.redis.index-name:embedding-index}")
    private String indexName;

    @Value("${app.rag.retriever.max-results:5}")
    private Integer maxResults;

    public BM25Retriever(
            @Value("${spring.data.redis.host:127.0.0.1}") String redisHost,
            @Value("${spring.data.redis.port:6379}") Integer redisPort,
            @Value("${spring.data.redis.password:}") String redisPassword,
            ObjectMapper objectMapper) {
        boolean hasPassword = redisPassword != null && !redisPassword.isEmpty();
        // 与 VectorStoreConfig 一致：有密码时 user 必须非空，Jedis 才会走鉴权分支
        this.jedis = hasPassword
                ? new JedisPooled(redisHost, redisPort, "default", redisPassword)
                : new JedisPooled(redisHost, redisPort);
        this.objectMapper = objectMapper;
    }

    public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
        if (knowledgeId == null) {
            throw new IllegalArgumentException("knowledgeId 不能为空，无法按知识库隔离检索");
        }
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        String textQuery = escapeQuery(query.trim());
        if (textQuery.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 知识库隔离：NUMERIC 字段用区间 @knowledgeId:[id id]
        // 2. @text:(...) 走 RediSearch 全文检索（默认 BM25）
        String redisQuery = String.format(
                "@%s:[%d %d] @%s:(%s)",
                VectorMetadataKeys.KNOWLEDGE_ID,
                knowledgeId,
                knowledgeId,
                TEXT_FIELD,
                textQuery);

        Query searchQuery = new Query(redisQuery)
                .limit(0, maxResults)
                .dialect(2);

        log.info("BM25 检索开始, index={}, knowledgeId={}, maxResults={}, query={}",
                indexName, knowledgeId, maxResults, redisQuery);

        // 3. 执行全文检索
        SearchResult searchResult = jedis.ftSearch(indexName, searchQuery);

        // 4. 结果映射
        return searchResult.getDocuments().stream()
                .map(this::convertToRetrievedChunk)
                .collect(Collectors.toList());
    }

    /**
     * 将 RediSearch Document 转换为业务对象 RetrievedChunk
     * 注意：与 VectorRetriever 对齐，只读取规范化的 VectorMetadataKeys
     */
    private RetrievedChunk convertToRetrievedChunk(Document document) {
        RetrievedChunk chunk = new RetrievedChunk();
        // BM25 与向量分不在同一量纲，写入 bm25Score，勿写入通用 score
        chunk.setBm25Score(document.getScore());

        log.debug("==========BM25 检索结果========== bm25Score:{}", chunk.getBm25Score());

        Map<String, Object> properties = parseProperties(document);
        if (properties.isEmpty()) {
            return chunk;
        }

        Object text = properties.get(TEXT_FIELD);
        if (text != null) {
            chunk.setContent(String.valueOf(text));
        }

        log.debug("Redis properties: {}", properties);

        if (properties.containsKey(VectorMetadataKeys.PAGE)) {
            chunk.setPage(toInteger(properties.get(VectorMetadataKeys.PAGE)));
        }

        if (properties.containsKey(VectorMetadataKeys.CHUNK_ID)) {
            chunk.setChunkId(toLong(properties.get(VectorMetadataKeys.CHUNK_ID)));
        }

        if (properties.containsKey(VectorMetadataKeys.DOCUMENT_ID)) {
            chunk.setDocumentId(toLong(properties.get(VectorMetadataKeys.DOCUMENT_ID)));
        }

        return chunk;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseProperties(Document document) {
        if (!document.hasProperty(JSON_ROOT)) {
            return Collections.emptyMap();
        }
        try {
            Object parsed = objectMapper.readValue(document.getString(JSON_ROOT), Object.class);
            // RedisJSON 有时返回 [{...}]，有时返回 {...}
            if (parsed instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                return (Map<String, Object>) list.get(0);
            }
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("解析 RediSearch 文档 JSON 失败, id={}", document.getId(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 转义 RediSearch 查询特殊字符，按空白切词后空格连接（AND 语义）
     */
    private String escapeQuery(String query) {
        StringBuilder sb = new StringBuilder();
        for (String token : query.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(token.replaceAll("([,.<>{}\\[\\]\"':;!@#$%^&*()\\-+=~|/\\\\])", "\\\\$1"));
        }
        return sb.toString();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
