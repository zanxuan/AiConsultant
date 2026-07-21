package com.zx.consultant.chat.dto;

import lombok.Data;
import java.util.List;
/*
 * 聊天响应DTO
 * @author zx
 * @date 2026-07-20
 */
@Data
public class ChatResp {
    /**
     * 回答
     */
    private String answer;
    /**
     * 引用来源列表
     */
    private List<String> sources; // 引用来源列表
}