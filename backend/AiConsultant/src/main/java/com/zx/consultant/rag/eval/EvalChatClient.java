package com.zx.consultant.rag.eval;

import java.util.List;
import dev.langchain4j.data.message.ChatMessage;

/**
 * 评测专用的非流式调用。被测模型和两个裁判各用各的客户端，温度都固定为 0。
 * 不走线上 {@code ChatModel} Bean，也不走 {@code LLMService#generateAnswer}。
 */
public interface EvalChatClient {

    String generationModelName();

    /** 忠实度裁判，当前是 DeepSeek */
    String faithfulnessJudgeModelName();

    /** 正确性裁判，当前是副模型 qwen3.8-flash */
    String correctnessJudgeModelName();

    /** 被测模型生成答案。生成评测关闭时不发请求 */
    String generate(List<ChatMessage> messages);

    /** 忠实度裁判打分。生成评测关闭时不发请求 */
    String judgeFaithfulness(List<ChatMessage> messages);

    /** 正确性裁判打分。生成评测关闭时不发请求 */
    String judgeCorrectness(List<ChatMessage> messages);
}
