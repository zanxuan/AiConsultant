package com.zx.consultant.rag.service.Impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zx.consultant.rag.service.CitationService;

@Service
public class CitationServiceImpl implements CitationService {

    @Override
    public String extractCitations(String llmResponse, List<String> retrievedDocs) {
        // TODO: 从回答和文档中提取引用来源
        return "Redis最佳实践.pdf";
    }
}
