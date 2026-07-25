package com.zx.consultant.chat.dto;

import lombok.Data;

/*
 * 聊天请求DTO
 * @author zx
 * @date 2026-07-20
 */
@Data
public class ChatReq {
    /**
     * 会话ID
     */
    private Long conversationId;
    /**
     * 消息
     */
    private String message;
}