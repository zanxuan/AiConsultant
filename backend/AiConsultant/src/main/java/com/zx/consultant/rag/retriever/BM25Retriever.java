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
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;

/**
 * BM25 关键词检索器。
 * <p>
 * 使用独立的中文索引 {@code app.rag.redis.bm25-index-name}，不检索、不删除、不重建
 * 向量索引 {@code embedding-index}。文档仍是向量入库写下的 {@code embedding:} JSON，
 * 中文索引只多挂一份 {@code LANGUAGE chinese} 倒排。
 */
@Slf4j
@Component
public class BM25Retriever {

    /** RedisEmbeddingStore 默认标量文本字段名 */
    private static final String TEXT_FIELD = "text";

    private static final String JSON_ROOT = "$";

    /** 与 langchain4j RedisEmbeddingStore 写死的 key 前缀一致，不复制文档 */
    static final String KEY_PREFIX = "embedding:";

    /** 索引和查询使用同一中文分词器 */
    static final String QUERY_LANGUAGE = "chinese";

    private final JedisPooled jedis;
    private final ObjectMapper objectMapper;

    /** 本次 retrieve 实际提交给 RediSearch 的查询串，读完必须清理 */
    private static final ThreadLocal<String> EXECUTED_QUERY = new ThreadLocal<>();

    private final Object indexLock = new Object();

    private volatile boolean chineseIndexReady;

    /** 只给 BM25 用。不能和向量索引同名 */
    @Value("${app.rag.redis.bm25-index-name:embedding-bm25-zh}")
    private String indexName;

    /** 只用于拒绝误操作向量索引，BM25 不检索、不删除、不重建它 */
    @Value("${app.rag.redis.index-name:embedding-index}")
    private String vectorIndexName;

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
        return retrieve(query, knowledgeId, maxResults);
    }

    /**
     * 与默认检索同一条 RediSearch 查询，仅 LIMIT 可覆盖。更大的 limit 只给诊断用。
     */
    public List<RetrievedChunk> retrieve(String query, Long knowledgeId, int resultLimit) {
        // 先清掉上一次残留，避免线程复用把别的查询串带进来
        EXECUTED_QUERY.remove();
        if (knowledgeId == null) {
            throw new IllegalArgumentException("knowledgeId 不能为空，无法按知识库隔离检索");
        }
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 标点按中文分词边界切开；检索前再按中文分词结果改成 OR
        String textQuery = ChineseBm25QueryBuilder.build(query.trim());
        if (textQuery.isEmpty()) {
            return Collections.emptyList();
        }

        ensureChineseIndex();

        // 1. 知识库隔离：NUMERIC 字段用区间 @knowledgeId:[id id]
        // 2. 先让中文分词器切出词项，再改成 OR。过滤、RRF 和 TopK 仍走原来的结果
        String redisQuery = buildRedisQuery(knowledgeId, orTextQuery(textQuery));

        Query searchQuery = new Query(redisQuery)
                .limit(0, resultLimit)
                .setLanguage(QUERY_LANGUAGE)
                .dialect(2);

        log.info("BM25 检索开始, index={}, knowledgeId={}, maxResults={}, query={}",
                indexName, knowledgeId, resultLimit, redisQuery);

        try {
            EXECUTED_QUERY.set(redisQuery);
            // 3. 执行全文检索
            SearchResult searchResult = jedis.ftSearch(indexName, searchQuery);

            // 4. 结果映射
            return searchResult.getDocuments().stream()
                    .map(this::convertToRetrievedChunk)
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            EXECUTED_QUERY.remove();
            throw e;
        }
    }

    /**
     * 取出本次检索实际执行的 Redis 查询串，并清理 ThreadLocal。
     * 没有执行查询时返回 null。
     */
    public String consumeExecutedQuery() {
        try {
            return EXECUTED_QUERY.get();
        } finally {
            EXECUTED_QUERY.remove();
        }
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
     * 用同一套中文分词解释查询。解释失败或没有词项时，仍用原来的查询串。
     */
    private String orTextQuery(String textQuery) {
        try {
            Query explainQuery = new Query("@text:(" + textQuery + ")")
                    .setLanguage(QUERY_LANGUAGE)
                    .dialect(2);
            String orQuery = ChineseBm25QueryBuilder.toOrQuery(jedis.ftExplain(indexName, explainQuery));
            if (!orQuery.isEmpty()) {
                return orQuery;
            }
        } catch (RuntimeException e) {
            log.warn("BM25 中文分词解释失败，改用原查询", e);
        }
        return textQuery;
    }

    public String buildRedisQuery(Long knowledgeId, String textQuery) {
        return String.format(
                "@%s:[%d %d] @%s:(%s)",
                VectorMetadataKeys.KNOWLEDGE_ID,
                knowledgeId,
                knowledgeId,
                TEXT_FIELD,
                textQuery);
    }

    /** 与检索实际使用的中文分词规则一致，供诊断打印查询词。 */
    public String previewEscapedQuery(String query) {
        return ChineseBm25QueryBuilder.build(query);
    }

    public String getIndexName() {
        return indexName;
    }

    /**
     * 确保中文 BM25 索引存在。已存在则只校验语言和前缀，不删除、不重建。
     * 向量索引 {@code embedding-index} 不在这里创建或修改。
     */
    public void ensureChineseIndex() {
        if (chineseIndexReady) {
            return;
        }
        synchronized (indexLock) {
            if (chineseIndexReady) {
                return;
            }
            if (indexName == null || indexName.isBlank() || indexName.equals(vectorIndexName)) {
                throw new IllegalStateException("BM25 索引名必须独立于向量索引 " + vectorIndexName);
            }
            try {
                Map<String, Object> info = jedis.ftInfo(indexName);
                assertChineseIndexCompatible(info);
                waitUntilIndexed();
            } catch (JedisDataException e) {
                if (!isMissingIndex(e)) {
                    throw e;
                }
                createChineseIndex();
                waitUntilIndexed();
            }
            chineseIndexReady = true;
        }
    }

    /**
     * 在同一批 {@code embedding:} 文档上另建中文倒排。
     * 不调用 FT.DROPINDEX，因此不会动到已有的 embedding-index。
     */
    private void createChineseIndex() {
        List<SchemaField> fields = List.of(
                TextField.of("$." + TEXT_FIELD).as(TEXT_FIELD),
                NumericField.of("$." + VectorMetadataKeys.KNOWLEDGE_ID).as(VectorMetadataKeys.KNOWLEDGE_ID));
        log.info("创建中文 BM25 索引, index={}, prefix={}, language={}", indexName, KEY_PREFIX, QUERY_LANGUAGE);
        try {
            jedis.ftCreate(
                    indexName,
                    FTCreateParams.createParams()
                            .on(IndexDataType.JSON)
                            .addPrefix(KEY_PREFIX)
                            .language(QUERY_LANGUAGE),
                    fields);
            assertChineseIndexCompatible(jedis.ftInfo(indexName));
        } catch (JedisDataException e) {
            // 并发启动时另一边已经建好，转为校验，仍然不删除
            if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("already exists")) {
                throw e;
            }
            assertChineseIndexCompatible(jedis.ftInfo(indexName));
        }
    }

    private void assertChineseIndexCompatible(Map<String, Object> info) {
        String language = asText(definitionValue(info, "default_language"));
        if (!QUERY_LANGUAGE.equalsIgnoreCase(language)) {
            throw new IllegalStateException(
                    "中文 BM25 索引已存在，但语言不是 chinese，不会删除重建: " + indexName
                            + ", definition=" + info.get("index_definition"));
        }
        Object prefixes = definitionValue(info, "prefixes");
        boolean prefixOk = false;
        if (prefixes instanceof List<?> list) {
            prefixOk = list.stream().anyMatch(item -> KEY_PREFIX.equals(asText(item)));
        } else if (prefixes != null) {
            prefixOk = KEY_PREFIX.equals(asText(prefixes));
        }
        if (!prefixOk) {
            throw new IllegalStateException(
                    "中文 BM25 索引前缀不是 " + KEY_PREFIX + "，不会删除重建: " + indexName);
        }
    }

    /** RediSearch 扫描已有 JSON。扫完才检索，避免评测打到半成品索引。 */
    private void waitUntilIndexed() {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
        while (true) {
            Map<String, Object> info = jedis.ftInfo(indexName);
            long indexing = toLong(info.get("indexing")) == null ? 0L : toLong(info.get("indexing"));
            double percent = toDouble(info.get("percent_indexed"), indexing == 0L ? 1.0d : 0.0d);
            if (indexing == 0L && percent >= 1.0d) {
                Object numDocs = info.get("num_docs");
                log.info("中文 BM25 索引就绪, index={}, num_docs={}, failures={}",
                        indexName, numDocs, info.get("hash_indexing_failures"));
                if (toLong(numDocs) != null && toLong(numDocs) == 0L) {
                    log.warn("中文 BM25 索引没有文档。向量索引 {} 的 JSON 未改动，BM25 不会重建它", vectorIndexName);
                }
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("中文 BM25 索引扫描超时: " + indexName);
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待中文 BM25 索引时被中断", e);
            }
        }
    }

    private static Object definitionValue(Map<String, Object> info, String key) {
        if (info.containsKey(key)) {
            return info.get(key);
        }
        Object definition = info.get("index_definition");
        if (definition instanceof Map<?, ?> map && map.containsKey(key)) {
            return map.get(key);
        }
        if (definition instanceof List<?> list) {
            for (int i = 0; i + 1 < list.size(); i++) {
                if (key.equals(asText(list.get(i)))) {
                    return list.get(i + 1);
                }
            }
        }
        return null;
    }

    private static String asText(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static boolean isMissingIndex(JedisDataException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("unknown index") || lower.contains("no such index");
    }

    private static double toDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
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
