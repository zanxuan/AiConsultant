package com.zx.consultant.common.exception;

/**
 * 业务异常
 */
public class BaseException extends RuntimeException {

    public BaseException() {
    }

    public BaseException(String msg) {
        super(msg);
    }

    // 新增：消息+原始异常，用于异常链传递，打印完整堆栈
    public BaseException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
