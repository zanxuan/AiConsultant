package com.zx.consultant.rag.eval;

import java.util.List;
import com.zx.consultant.common.exception.LLMException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 评测模型客户端。
 * <p>
 * 被测模型使用主模型同一套接口和密钥，模型名默认 qwen-plus。
 * 裁判使用副模型。qwen-turbo 已下架，当前副模型是 qwen3.8-flash，接口仍是 compatible-mode。
 * 两个客户端都在这里单独构建，温度固定为 0，不重试，也不在失败时互相降级。
 */
@Component
public class EvalChatClientImpl implements EvalChatClient {

    /** 评测温度不随线上对话配置变化 */
    static final double EVAL_TEMPERATURE = 0.0d;

    private final boolean generationEnabled;
    private final String generationModelName;
    private final String judgeModelName;
    private final ChatModel generationModel;
    private final ChatModel judgeModel;

    public EvalChatClientImpl(
            @Value("${app.rag.eval.generation-enabled:false}") boolean generationEnabled,
            @Value("${app.rag.eval.generation-model:qwen-plus}") String generationModelName,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String generationBaseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String generationApiKey,
            @Value("${app.llm.secondary.model-name}") String judgeModelName,
            @Value("${app.llm.secondary.base-url}") String judgeBaseUrl,
            @Value("${app.llm.secondary.api-key}") String judgeApiKey,
            @Value("${app.llm.secondary.log-requests:true}") boolean logRequests,
            @Value("${app.llm.secondary.log-responses:true}") boolean logResponses) {
        this.generationEnabled = generationEnabled;
        this.generationModelName = generationModelName;
        this.judgeModelName = judgeModelName;
        this.generationModel = build(generationBaseUrl, generationApiKey, generationModelName, logRequests, logResponses);
        this.judgeModel = build(judgeBaseUrl, judgeApiKey, judgeModelName, logRequests, logResponses);
    }

    @Override
    public String generationModelName() {
        return generationModelName;
    }

    @Override
    public String judgeModelName() {
        return judgeModelName;
    }

    @Override
    public String generate(List<ChatMessage> messages) {
        ensureEnabled();
        return complete(generationModel, generationModelName, messages);
    }

    @Override
    public String judge(List<ChatMessage> messages) {
        ensureEnabled();
        return complete(judgeModel, judgeModelName, messages);
    }

    /** 供测试确认温度写在评测客户端上，而不是线上 Bean */
    ChatRequestParameters generationParameters() {
        return generationModel.defaultRequestParameters();
    }

    ChatRequestParameters judgeParameters() {
        return judgeModel.defaultRequestParameters();
    }

    private void ensureEnabled() {
        if (!generationEnabled) {
            throw new IllegalStateException("生成评测未开启，配置 app.rag.eval.generation-enabled=false");
        }
    }

    /**
     * 只发一次。maxRetries 设为 0，避免客户端自己重试后再把失败留给上层。
     * 被测模型失败时不会改叫裁判模型。
     */
    private static ChatModel build(String baseUrl, String apiKey, String modelName,
            boolean logRequests, boolean logResponses) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(EVAL_TEMPERATURE)
                .maxRetries(0)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    private static String complete(ChatModel model, String modelName, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new LLMException(modelName + " 的评测消息为空");
        }
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .build();
        try {
            ChatResponse response = model.chat(request);
            if (response == null || response.aiMessage() == null || response.aiMessage().text() == null
                    || response.aiMessage().text().isBlank()) {
                throw new LLMException(modelName + " 返回空回答");
            }
            return response.aiMessage().text();
        } catch (LLMException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LLMException(modelName + " 调用失败: " + e.getMessage(), e);
        }
    }
}
