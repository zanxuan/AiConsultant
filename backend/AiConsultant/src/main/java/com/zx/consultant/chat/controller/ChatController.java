package com.zx.consultant.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.zx.consultant.chat.dto.ChatReq;
import com.zx.consultant.chat.dto.ChatTaskResp;
import com.zx.consultant.chat.service.ChatService;
import com.zx.consultant.common.result.Result;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 提交问答：校验会话、用户问题落库、创建任务后立即返回 taskId。
     * 最终 ChatResp 由 GET /stream/{taskId} 以 SSE complete 事件推送。
     */
    @PostMapping
    public Result<ChatTaskResp> chat(@RequestBody ChatReq req) {
        return Result.success(chatService.ask(req));
    }

    /**
     * 按 taskId 订阅 SSE。连接保持打开，直到后台推送 complete 或超时。
     * Content-Type 头设置为 text/event-stream，这是 SSE 协议必须的响应头
     */
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String taskId) {
        // 获取 SSE 连接，并返回给前端
        return chatService.stream(taskId);
    }
   
}
