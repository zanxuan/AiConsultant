package com.zx.consultant.memory.service;


import java.util.List;

import com.zx.consultant.chat.entity.Message;

public interface MemoryService {
    /**
     * 获取短期记忆（Redis 滑动窗口 + 可选摘要），转换为 Message 列表供 Rewrite/Prompt 使用
     * @param conversationId 会话ID（session_id）
     * @param limit 兼容旧调用；实际窗口由 Redis 摘要策略管理，该参数可忽略
     * @return 摘要（若有）+ 窗口内对话，时间正序
     */
    List<Message> getRecentMessages(Long conversationId, int limit);

    /**
     * 将一轮对话（用户问题 + AI 回答）写入 Redis 短期记忆；
     * 超过 32k 文字或 40 轮时触发摘要压缩。
     */
    void appendTurn(Long conversationId, String userQuestion, String aiAnswer);
}
