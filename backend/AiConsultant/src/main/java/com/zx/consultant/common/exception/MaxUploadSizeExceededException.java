package com.zx.consultant.common.exception;

public class MaxUploadSizeExceededException extends BaseException{
    
    public MaxUploadSizeExceededException() {
    }

    public MaxUploadSizeExceededException(String msg) {
        super(msg);
    }
    
    public MaxUploadSizeExceededException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
