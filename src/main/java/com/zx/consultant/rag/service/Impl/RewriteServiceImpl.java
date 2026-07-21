package com.zx.consultant.rag.service.Impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zx.consultant.rag.service.RewriteService;

@Service
public class RewriteServiceImpl implements RewriteService {

    @Override
    public String rewriteQuery(String originalQuery, List<String> memory) {
        // TODO: 调用 Langchain4j ChatModel 进行 Query Rewrite
        return originalQuery; // 初始占位
    }
}
