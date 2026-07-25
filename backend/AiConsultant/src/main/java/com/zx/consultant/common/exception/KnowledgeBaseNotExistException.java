package com.zx.consultant.common.exception;

/**
 * 知识库不存在异常
 */
public class KnowledgeBaseNotExistException extends BaseException {

    private static final String DEFAULT_MSG = "知识库不存在";

    public KnowledgeBaseNotExistException() {
        super(DEFAULT_MSG);
    }

    public KnowledgeBaseNotExistException(String msg) {
        super(msg);
    }

    public KnowledgeBaseNotExistException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * 携带知识库ID，拼接提示文案
     */
    public KnowledgeBaseNotExistException(Long kbId) {
        super(DEFAULT_MSG + "，知识库ID：" + kbId);
    }
}