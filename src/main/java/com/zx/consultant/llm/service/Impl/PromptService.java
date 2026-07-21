package com.zx.consultant.llm.service.Impl;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PromptService {
    public String buildPrompt(String query, List<String> memory, List<String> docs) {
        // TODO: 组装 Prompt 模板
        return "Prompt Builder Result"; 
    }
}