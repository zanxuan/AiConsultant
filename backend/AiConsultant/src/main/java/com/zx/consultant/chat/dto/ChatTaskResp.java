package com.zx.consultant.chat.dto;

import lombok.Data;

/**
 * POST /api/v1/chat 的返回值，只携带 taskId。
 * 最终 answer / references 仍由 ChatResp 通过 SSE 推送。
 */
@Data
public class ChatTaskResp {
    /**
     * 本次问答任务 ID，用于 GET /api/v1/chat/stream/{taskId}
     */
    private String taskId;
}
