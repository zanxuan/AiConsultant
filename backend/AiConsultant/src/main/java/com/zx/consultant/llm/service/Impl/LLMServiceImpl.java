package com.zx.consultant.llm.service.Impl;

import com.zx.consultant.common.constant.MessageRole;
import com.zx.consultant.common.exception.LLMException;
import com.zx.consultant.common.trace.TraceContext;
import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LLMServiceImpl implements LLMService {

    private final ChatModel primaryModel;
    private final ChatModel secondaryModel;
    private final StreamingChatModel streamingChatModel;
    private final String primaryModelName;
    private final String secondaryModelName;

    public LLMServiceImpl(
            ChatModel chatModel,
            StreamingChatModel streamingChatModel,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String primaryModelName,
            @Value("${app.llm.secondary.base-url}") String secondaryBaseUrl,
            @Value("${app.llm.secondary.api-key}") String secondaryApiKey,
            @Value("${app.llm.secondary.model-name}") String secondaryModelName,
            @Value("${app.llm.secondary.log-requests:true}") boolean secondaryLogRequests,
            @Value("${app.llm.secondary.log-responses:true}") boolean secondaryLogResponses) {
        this.primaryModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.primaryModelName = primaryModelName;
        this.secondaryModelName = secondaryModelName;
        // 副模型单独构建，避免再注册 ChatModel Bean 与主模型注入冲突
        this.secondaryModel = OpenAiChatModel.builder()
                .baseUrl(secondaryBaseUrl)
                .apiKey(secondaryApiKey)
                .modelName(secondaryModelName)
                .logRequests(secondaryLogRequests)
                .logResponses(secondaryLogResponses)
                .build();
    }

    /**
     * 核心转换逻辑：将我们自己的 PromptRequest 精准映射为 LangChain4j 的消息体
     */
    private List<ChatMessage> buildChatMessages(PromptRequest request) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. 注入系统设定 (SystemMessage)
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(SystemMessage.from(request.getSystemPrompt()));
        }

        // 2. 注入历史对话（system=摘要上下文；user/assistant=真实轮次）
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            for (var msg : request.getHistory()) {
                if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
                    continue;
                }
                if (MessageRole.SYSTEM.equalsIgnoreCase(msg.getRole())) {
                    messages.add(SystemMessage.from(msg.getContent()));
                } else if (MessageRole.USER.equalsIgnoreCase(msg.getRole())) {
                    messages.add(UserMessage.from(msg.getContent()));
                } else if (MessageRole.ASSISTANT.equalsIgnoreCase(msg.getRole())) {
                    messages.add(AiMessage.from(msg.getContent()));
                }
            }
        }

        // 3. 注入本次的检索上下文和最终问题 (作为最新的一条 UserMessage)
        String finalUserText = request.getContext() + "\n\n【用户问题】:\n" + request.getUserQuery();
        messages.add(UserMessage.from(finalUserText));

        return messages;
    }

    /**
     * 非流式完整回答：主模型最多试 2 次，仍失败再降级到副模型
     */
    @Override
    public String generateAnswer(PromptRequest promptRequest) {
        List<ChatMessage> messages = buildChatMessages(promptRequest);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        try {
            ChatResponse chatResponse = callPrimary(chatRequest);
            TraceContext.setModelUsed(primaryModelName);
            TraceContext.setFallbackTriggered(false);
            TraceContext.setFallbackReason(null);
            return chatResponse.aiMessage().text();
        } catch (Exception primaryEx) {
            log.warn("主模型({})两次均失败，降级到副模型({}): {}",
                    primaryModelName, secondaryModelName, primaryEx.getMessage());
            TraceContext.setFallbackTriggered(true);
            TraceContext.setFallbackReason("PRIMARY_LLM_FAILED: " + primaryEx.getMessage());
            try {
                ChatResponse chatResponse = secondaryModel.chat(chatRequest);
                TraceContext.setModelUsed(secondaryModelName);
                return chatResponse.aiMessage().text();
            } catch (Exception secondaryEx) {
                TraceContext.setModelUsed(secondaryModelName);
                throw new LLMException("主/副模型均调用失败: " + secondaryEx.getMessage(), secondaryEx);
            }
        }
    }

    /** 主模型最多调用 2 次（首次 + 1 次重试），都失败则抛出最后一次异常 */
    private ChatResponse callPrimary(ChatRequest request) throws Exception {
        Exception lastException = null;
        for (int i = 0; i < 2; i++) {
            try {
                return primaryModel.chat(request);
            } catch (Exception e) {
                lastException = e;
                log.warn("主模型({})第{}次调用失败: {}", primaryModelName, i + 1, e.getMessage());
            }
        }
        throw lastException;
    }

    /**
     * 流式输出（对接前端SSE，适配聊天打字机效果）
     */
    @Override
    public Flux<String> streamGenerateAnswer(PromptRequest promptRequest) {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        
        try {
            // 组装结构化消息
            List<ChatMessage> messages = buildChatMessages(promptRequest);

            // 构建 ChatRequest
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .build();

            // 传入 chatRequest 开启流式调用
            streamingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    sink.tryEmitNext(partialResponse);
                }
                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    sink.tryEmitComplete();
                }
                @Override
                public void onError(Throwable error) {
                    sink.tryEmitError(new LLMException("流式调用失败", error));
                }
            });
        } catch (Exception e) {
            // 捕获组装阶段可能发生的异常
            sink.tryEmitError(new LLMException("构建流式请求失败: " + e.getMessage(), e));
        }
        
        return sink.asFlux();
    }
}
