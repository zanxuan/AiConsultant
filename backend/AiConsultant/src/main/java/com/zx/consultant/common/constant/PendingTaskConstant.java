package com.zx.consultant.common.constant;

/**
 * PendingTask Redis 约定。它只是短生命周期状态，不是会话模式。
 */
public final class PendingTaskConstant {

    private PendingTaskConstant() {}

    /**
     * Redis Key：pending:task:{userId}:{conversationId}
     * 每个会话最多一个待处理任务。
     */
    public static final String REDIS_KEY_PREFIX = "pending:task:";

    /** 超时未处理则自动失效，避免长期悬挂。 */
    public static final long TTL_MINUTES = 30;
}
