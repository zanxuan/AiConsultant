package com.zx.consultant.rag.eval;

import java.util.List;
import dev.langchain4j.data.message.ChatMessage;

/**
 * 评测专用的非流式调用。被测模型和裁判各用各的客户端，温度都固定为 0。
 * 不走线上 {@code ChatModel} Bean，也不走 {@code LLMService#generateAnswer}。
 */
public interface EvalChatClient {

    String generationModelName();

    String judgeModelName();

    /** 被测模型生成答案。生成评测关闭时不发请求 */
    String generate(List<ChatMessage> messages);

    /** 裁判模型打分。生成评测关闭时不发请求 */
    String judge(List<ChatMessage> messages);
}
