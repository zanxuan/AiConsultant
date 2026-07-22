package com.zx.consultant.llm.service.Impl;

import com.zx.consultant.common.constant.MessageRole;
import com.zx.consultant.common.exception.LLMException;
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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

@Service
public class LLMServiceImpl implements LLMService {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    public LLMServiceImpl(ChatModel chatModel, StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
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

        // 2. 注入历史对话 (区分 User 和 AI 角色)
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            for (var msg : request.getHistory()) {
                if (MessageRole.USER.equalsIgnoreCase(msg.getRole())) {
                    messages.add(UserMessage.from(msg.getContent()));
                } else if (MessageRole.ASSISTANT.equalsIgnoreCase(msg.getRole())) {
                    messages.add(AiMessage.from(msg.getContent())); // 注意：LangChain4j 里叫 AiMessage
                }
            }
        }

        // 3. 注入本次的检索上下文和最终问题 (作为最新的一条 UserMessage)
        String finalUserText = request.getContext() + "\n\n【用户问题】:\n" + request.getUserQuery();
        messages.add(UserMessage.from(finalUserText));

        return messages;
    }

    /**
     * 非流式完整回答
     */
    @Override
    public String generateAnswer(PromptRequest promptRequest) {
        try {
            // 组装结构化消息
            List<ChatMessage> messages = buildChatMessages(promptRequest);

            // 构建 ChatRequest
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .build();
                    
            // 调用大模型
            ChatResponse chatResponse = chatModel.chat(chatRequest);
            return chatResponse.aiMessage().text();
        } catch(Exception e){
            throw new LLMException("模型调用失败: " + e.getMessage(), e);
        }
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