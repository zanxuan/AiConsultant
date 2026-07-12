package com.zx.consultant.user.controller;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zx.consultant.chat.ConsultantService;

import reactor.core.publisher.Flux;

@RestController
public class ChatController {
    @Autowired
    private ConsultantService consultantService;

    @RequestMapping(value = "/chat",produces = "text/html;charset=utf-8")//解决乱码问题
    public Flux<String> chat(String memoryId,String message){
        Flux<String> result = consultantService.chat(memoryId,message);
        return result;
    }

    /*@RequestMapping("/chat")
    public String chat(String message){
        String result = consultantService.chat(message);
        return result;
    }*/

    /*@Autowired
    private OpenAiChatModel model;
    @RequestMapping("/chat")
    public String chat(String message){//浏览器传递的用户问题
        String result = model.chat(message);
        return result;
    }*/
}
