package com.zx.consultant.rag.service.impl;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.zx.consultant.common.constant.VectorMetadataKeys;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.service.RetrieverService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 检索服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrieverServiceImpl implements RetrieverService {

    private final EmbeddingModel embeddingModel;

    //自动注入redisEmbeddingStore()[目前暂时支持redis]
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${app.rag.retriever.max-results:5}")
    private Integer maxResults;

    @Value("${app.rag.retriever.min-score:0.75}")
    private Double minScore;

    
    @Override
    public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
        if (knowledgeId == null) {
            throw new IllegalArgumentException("knowledgeId 不能为空，无法按知识库隔离检索");
        }

        // TODO(V2)：支持 Hybrid Search（BM25 + Vector）,增加 Rerank 重排序
        // 1. 向量化提问
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 知识库隔离：NUMERIC 字段不能用 isEqualTo（会生成非法 @knowledgeId:[id]）
        //    必须用 gte + lte 拼出合法区间：@knowledgeId:[id id]
        Filter knowledgeFilter = metadataKey(VectorMetadataKeys.KNOWLEDGE_ID).isGreaterThanOrEqualTo(knowledgeId)
                .and(metadataKey(VectorMetadataKeys.KNOWLEDGE_ID).isLessThanOrEqualTo(knowledgeId));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(knowledgeFilter)
                .build();

        log.info("向量检索开始, knowledgeId={}, maxResults={}, minScore={}", knowledgeId, maxResults, minScore);

        // 3. 执行检索
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // 4. 结果映射
        return searchResult.matches().stream()
                .map(this::convertToRetrievedChunk)
                .collect(Collectors.toList());
    }

    /**
     * 将 LangChain4j 的 EmbeddingMatch 转换为业务对象 RetrievedChunk
     * 注意：保持绝对干净，不处理任何历史遗留或第三方兼容字段，完全依赖规范化的 VectorMetadataKeys
     */
    private RetrievedChunk convertToRetrievedChunk(EmbeddingMatch<TextSegment> match) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setScore(match.score());
        
        log.debug("==========检索结果========== score:{}", chunk.getScore());

        TextSegment segment = match.embedded();
        if (segment != null) {
            chunk.setContent(segment.text());
            log.debug("Redis metadata: {}", segment.metadata());

            if (segment.metadata().containsKey(VectorMetadataKeys.PAGE)) {
                chunk.setPage(segment.metadata().getInteger(VectorMetadataKeys.PAGE));
            }
            
            if (segment.metadata().containsKey(VectorMetadataKeys.CHUNK_ID)) {
                chunk.setChunkId(segment.metadata().getLong(VectorMetadataKeys.CHUNK_ID));
            }

            if (segment.metadata().containsKey(VectorMetadataKeys.DOCUMENT_ID)) {
                chunk.setDocumentId(segment.metadata().getLong(VectorMetadataKeys.DOCUMENT_ID));
            }
        }

        return chunk;
    }
}
