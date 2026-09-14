package com.zx.consultant.chat.service;

import com.zx.consultant.chat.dto.ChatReq;
import com.zx.consultant.chat.dto.ChatTaskResp;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ChatService
 */
public interface ChatService {

    /**
     * 提交问答任务，立即返回 taskId。最终 ChatResp 通过 SSE 推送。
     */
    ChatTaskResp ask(ChatReq req);

    /**
     * 按 taskId 建立 SSE 长连接。
     */
    SseEmitter stream(String taskId);
}
