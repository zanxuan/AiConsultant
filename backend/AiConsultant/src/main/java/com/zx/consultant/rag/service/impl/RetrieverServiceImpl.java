package com.zx.consultant.rag.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.retriever.HybridRetriever;
import com.zx.consultant.rag.service.RetrieverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 检索服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrieverServiceImpl implements RetrieverService {

    private final HybridRetriever hybridRetriever;

    @Override
    public List<RetrievedChunk> retrieve(String query, Long knowledgeId) {
        // TODO(V2)：增加 Rerank 重排序
        return hybridRetriever.retrieve(query, knowledgeId);
    }
}
