package com.zx.consultant.chat.dto;

import lombok.Data;

@Data
public class ConversationCreateReq {
    private Long knowledgeId;
    private String title;
}