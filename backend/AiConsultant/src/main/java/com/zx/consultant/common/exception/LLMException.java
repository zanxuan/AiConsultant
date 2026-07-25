package com.zx.consultant.common.exception;

public class LLMException extends BaseException {

    public LLMException() {
    }

    public LLMException(String msg) {
        super(msg);
    }
    
    public LLMException(String msg, Throwable cause) {
        super(msg, cause); // 调用父类现成的双参构造
    }

}
