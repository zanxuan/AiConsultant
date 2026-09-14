package com.zx.consultant.chat.task;

import com.zx.consultant.chat.dto.ChatResp;
import lombok.Data;

/**
 * 内存中的 Chat 任务。用于给一次问答分配 taskId，并让 SSE 按 taskId 找到任务。
 * 不是 PendingTask（后者是会话级 Redis 澄清状态，职责不同）。
 */
@Data
public class ChatTask {
    private String taskId;
    private Long conversationId;
    /**
     * 后台完成后缓冲的 ChatResp。
     * SSE 可能晚于 Workflow 建立连接，先存在这里再补推。
     */
    private ChatResp result;
}
