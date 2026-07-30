package com.zx.consultant.common.constant;

public final class MessageRole {

    private MessageRole() {}

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
    /** 系统上下文（如历史摘要），不是模型对用户的回复 */
    public static final String SYSTEM = "system";
}
