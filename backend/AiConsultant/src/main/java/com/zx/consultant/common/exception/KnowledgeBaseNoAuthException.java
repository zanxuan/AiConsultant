package com.zx.consultant.common.exception;

/**
 * 知识库操作权限不足异常
 */
public class KnowledgeBaseNoAuthException extends BaseException {

    private static final String DEFAULT_MSG = "无该知识库操作权限";

    public KnowledgeBaseNoAuthException() {
        super(DEFAULT_MSG);
    }

    public KnowledgeBaseNoAuthException(String msg) {
        super(msg);
    }

    public KnowledgeBaseNoAuthException(String msg, Throwable cause) {
        super(msg, cause);
    }
}