package com.zx.consultant.llm.entity;

import com.zx.consultant.rag.entity.Message;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 结构化的 Prompt 请求对象，与底层 LLM 框架完全解耦
 */
@Data
public class PromptRequest {
    // 1. 角色与系统人设
    private String systemPrompt;
    
    // 2. 当前用户的具体提问（可能已经被 Rewrite 过）
    private String userPrompt;
    
    // 3. 历史会话上下文 (可选，看你是把历史拼在 userPrompt 里，还是作为独立的 Message 传递)
    private List<Message> history;
    
    // 4. 检索到的参考背景知识
    private String context;
    
    // 5. 动态变量字典 (用于动态替换 Prompt 模板里的占位符)
    private Map<String, Object> variables;
}