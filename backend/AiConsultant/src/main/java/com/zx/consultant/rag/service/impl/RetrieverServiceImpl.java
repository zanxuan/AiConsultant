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
        // 重排序在 HybridRetriever 内、RRF 截断之后。评测走 diagnose，必须和这里共用同一次 retrieve
        return hybridRetriever.retrieve(query, knowledgeId);
    }
}
