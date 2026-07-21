package com.zx.consultant.chat.controller;

import lombok.RequiredArgsConstructor; // 引入注解
import org.springframework.web.bind.annotation.*;
import com.zx.consultant.chat.dto.ChatReq;
import com.zx.consultant.chat.dto.ChatResp;
import com.zx.consultant.chat.service.ChatService;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor // Lombok 会在编译时自动生成包含所有 final 字段的构造函数
public class ChatController {

    private final ChatService chatService;

    // 不再需要手动写构造函数了！

    @PostMapping
    public ChatResp chat(@RequestBody ChatReq req) {
        return chatService.ask(req);
    }

     // 注：未来如果升级 SSE/流式输出，接口地址保持不变。

    // 只需修改 produces = MediaType.TEXT_EVENT_STREAM_VALUE

    // 并将返回类型改为 SseEmitter 或 Flux<ChatResp> 即可。
}