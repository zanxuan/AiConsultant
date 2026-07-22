package com.zx.consultant.llm.service;

import com.zx.consultant.llm.entity.PromptRequest;
import reactor.core.publisher.Flux;

public interface LLMService {

    /**
     * 普通一次性问答（非流式，返回完整字符串）
     * @param prompt 完整提示词（包含RAG检索上下文+用户问题）
     * @return 完整回答文本
     */
    String generateAnswer(PromptRequest promptRequest);

    /**
     * 流式问答（SSE，前端实时接收分段文字）
     * @param prompt 完整提示词
     * @return Flux 流式文本分段
     */
    Flux<String> streamGenerateAnswer(PromptRequest promptRequest);
}
