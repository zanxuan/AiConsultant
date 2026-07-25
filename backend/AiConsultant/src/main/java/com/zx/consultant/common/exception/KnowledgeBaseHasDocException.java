package com.zx.consultant.common.exception;

/**
 * 知识库下存在文档，无法删除异常
 */
public class KnowledgeBaseHasDocException extends BaseException {

    private static final String DEFAULT_MSG = "该知识库下还存在文档，无法删除";

    public KnowledgeBaseHasDocException() {
        super(DEFAULT_MSG);
    }

    public KnowledgeBaseHasDocException(String msg) {
        super(msg);
    }

    public KnowledgeBaseHasDocException(Long docCount) {
        super(DEFAULT_MSG + "，当前文档数量：" + docCount);
    }

    public KnowledgeBaseHasDocException(String msg, Throwable cause) {
        super(msg, cause);
    }
}