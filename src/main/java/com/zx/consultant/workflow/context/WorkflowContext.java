package com.zx.consultant.workflow.context;

import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.llm.entity.PromptRequest;

import lombok.Data;
import java.util.List;

/**
 * Workflow 上下文，负责在各个 Node 之间传递数据
 */
@Data
public class WorkflowContext {
    private Long conversationId;
    private String originalQuery; // 用户原始提问
    
    // 流程流转状态
    private String intent; // 意图识别结果
    private String rewrittenQuery; // 重写后的 Query
    private List<Message> memory; // 历史聊天记录
    private List<RetrievedChunk> retrievedDocuments; // 检索到的相关文档片段 (BM25 + Vector)
    private PromptRequest prompt; // 构建好的 Prompt
    private String llmResponse; // LLM 原始回答
    private String citations; // 提取的引用来源
    
    // 最终返回结果
    private String finalAnswer;
}