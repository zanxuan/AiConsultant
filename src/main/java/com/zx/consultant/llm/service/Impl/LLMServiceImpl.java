package com.zx.consultant.llm.service.Impl;

import com.zx.consultant.common.exception.LLMException;
import com.zx.consultant.llm.service.LLMService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

@Service
public class LLMServiceImpl implements LLMService {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    // 推荐做法：定义默认的系统角色设定（企业项目中也可以用 @Value 从 yml 配置文件动态读取）
    private static final String SYSTEM_PROMPT = "你是一个专业的AI咨询顾问，请给出准确、专业的回答。";

    // 依然使用构造器注入
    public LLMServiceImpl(ChatModel chatModel, StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

   /**
     * 非流式完整回答
     */
   @Override
   public String generateAnswer(String prompt) {
    // TODO :后期在PromptService中构建完整的Prompt，并调用此方法
       try {
           // 组合 SystemMessage 和 UserMessage
           List<ChatMessage> messages = List.of(
                   SystemMessage.from(SYSTEM_PROMPT), // 设定角色
                   UserMessage.from(prompt)           // 用户问题
           );

           // 构建 ChatRequest
           ChatRequest chatRequest = ChatRequest.builder()
                   .messages(messages)
                   .build();
                   
           // 调用通义千问qwen-plus
           ChatResponse chatResponse = chatModel.chat(chatRequest);
           // 返回AI完整文本
           return chatResponse.aiMessage().text();
       } catch(Exception e){
           throw new LLMException(
               "模型调用失败: " + e.getMessage(), e
           );
       }
   }

   /**
    * 流式输出（对接前端SSE，适配聊天打字机效果）
    */
   @Override
   public Flux<String> streamGenerateAnswer(String prompt) {
       // 【核心修改点】改为 multicast() 允许多订阅，避免前端重连或并发订阅时抛出异常
       Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
       
       // 组合 SystemMessage 和 UserMessage
       List<ChatMessage> messages = List.of(
               SystemMessage.from(SYSTEM_PROMPT), // 设定角色
               UserMessage.from(prompt)           // 用户问题
       );

       // 构建 ChatRequest
       ChatRequest chatRequest = ChatRequest.builder()
               .messages(messages)
               .build();

       // 传入 chatRequest
       streamingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
           // 分段内容实时推送
           @Override
           public void onPartialResponse(String partialResponse) {
               sink.tryEmitNext(partialResponse);
           }
           // 全部输出完成
           @Override
           public void onCompleteResponse(ChatResponse completeResponse) {
               sink.tryEmitComplete();
           }
           // 异常捕获与包装
           @Override
           public void onError(Throwable error) {
               // 【核心修改点】将底层异常统一包装为自定义的业务异常 LLMException
               sink.tryEmitError(new LLMException("流式调用失败", error));
           }
       });
       return sink.asFlux();
   }
}