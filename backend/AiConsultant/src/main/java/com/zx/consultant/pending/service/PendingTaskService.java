package com.zx.consultant.pending.service;

import com.zx.consultant.pending.model.PendingTask;

/**
 * 会话级 PendingTask 的读写。数据在 Redis，带 TTL，过期即视为任务结束。
 */
public interface PendingTaskService {

    /** 创建或刷新任务，并重置 TTL。 */
    void save(Long conversationId, PendingTask task);

    /** 读取当前会话未过期的任务；没有则返回 null。 */
    PendingTask get(Long conversationId);

    /** 任务完成、用户改问或会话删除时结束任务。 */
    void delete(Long conversationId);
}
