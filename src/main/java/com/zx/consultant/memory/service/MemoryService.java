package com.zx.consultant.memory.service;


import java.util.List;

import com.zx.consultant.chat.entity.Message;

public interface MemoryService {
    /**
     * 获取最近的 N 条历史对话记录（滑动窗口）
     * @param conversationId 会话ID
     * @param limit 限制条数（如 6 条，代表 3 轮对话）
     * @return 截断后的历史记录
     */
    List<Message> getRecentMessages(Long conversationId, int limit);
}