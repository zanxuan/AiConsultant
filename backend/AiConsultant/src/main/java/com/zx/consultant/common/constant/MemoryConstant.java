package com.zx.consultant.common.constant;

public  final class MemoryConstant {

    // 私有化构造器，防止被实例化
    private MemoryConstant() {}
    
    /**
     * 兼容旧调用的默认条数（实际窗口由 Redis 摘要策略管理）
     */
    public static final int MAX_HISTORY_MESSAGES = 6;

    /**
     * 短期记忆文字上限（字符数，非 token）
     */
    public static final int MAX_MEMORY_CHARS = 32_000;

    /**
     * 短期记忆最大对话轮次（一轮 = 用户问题 + AI 回答）
     */
    public static final int MAX_MEMORY_ROUNDS = 6;

    /**
     * Redis Key 前缀：memory:short:{userId}:{sessionId}
     */
    public static final String REDIS_KEY_PREFIX = "memory:short:";

    /**
     * 单条滚动摘要最大字符数（超出则二次 LLM 压缩）
     */
    public static final int MAX_MEMORY_SUMMARY_LENGTH = 600;
}
