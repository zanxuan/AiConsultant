package com.zx.consultant.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,//手动装配
        chatModel = "openAiChatModel",//指定模型[多模型的时候一定要指定这个,否则会报错]
        streamingChatModel = "openAiStreamingChatModel",
        //chatMemory = "chatMemory",//配置会话记忆对象
        chatMemoryProvider = "chatMemoryProvider",//配置会话记忆提供者对象
        contentRetriever = "contentRetriever",//配置向量数据库检索对象
        tools = "reservationTool"
)
//@AiService
public interface ConsultantService {
    //用于聊天的方法
    //public String chat(String message);

    //@SystemMessage("你是呼呼呼呼呼")
    @SystemMessage(fromResource = "systemPrompt.txt")
    //@UserMessage("你是呼呼，{{msg}}") 通过{{msg}}的方式, 动态的获取到用户传递的消息
    public Flux<String> chat(/*@V("msg")*/@MemoryId String memoryId, @UserMessage String message);
}
