package com.zx.consultant.rag.service;

import java.util.List;

import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.rag.entity.RetrievedChunk;

public interface PromptService {

    /**
     * 兼容入口：语义等同于 {@link #buildRagPrompt}，供现有 RAG 节点继续调用。
     */
    PromptRequest buildPrompt(String query, List<Message> memory, List<RetrievedChunk> docs);

    /**
     * RAG 场景：基于知识库检索片段、短期记忆和当前问题组装 Prompt。
     */
    PromptRequest buildRagPrompt(String query, List<Message> memory, List<RetrievedChunk> docs);

    /**
     * CHAT 场景：仅基于当前问题和短期记忆组装 Prompt，不使用检索文档。
     */
    PromptRequest buildChatPrompt(String query, List<Message> memory);
}
