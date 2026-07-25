package com.zx.consultant.rag.service;

import java.util.List;

import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.rag.entity.RetrievedChunk;

public interface PromptService {

    PromptRequest buildPrompt(String query, List<Message> memory, List<RetrievedChunk> docs);
}
