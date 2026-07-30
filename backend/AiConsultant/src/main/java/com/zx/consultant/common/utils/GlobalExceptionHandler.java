package com.zx.consultant.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

import com.zx.consultant.common.result.Result;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.common.constant.MessageConstant;
import com.zx.consultant.common.exception.MaxUploadSizeExceededException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常与系统异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常（预期内的用户或业务操作错误）
     */
    @ExceptionHandler(BaseException.class)
    public Result<Void> exceptionHandler(BaseException ex){
        // 💡 优化点 1：业务异常用 warn 记录，避免监控系统误报
        log.warn("业务异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获数据库唯一约束冲突异常（如用户名重复）
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result<Void> exceptionHandler(SQLIntegrityConstraintViolationException e){
        log.error("数据库约束异常：{}", e.getMessage());
        String message = e.getMessage();
        
        // Clever! 完美的字符串切片提取逻辑
        if(message != null && message.contains("Duplicate entry")){
            String[] split = message.split(" ");
            if (split.length > 2) {
                String name = split[2];
                return Result.error(name + MessageConstant.ALREADY_EXISIS); // 检查一下这里的拼写哦
            }
        }
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 💡 优化点 2：增加终极兜底异常处理，防止空指针等未知错误搞垮前端接口结构
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleRemainingException(Exception e) {
        // 真正的系统级崩溃，用 error 记录并打印完整的堆栈信息
        log.error("系统内核未捕获异常：", e); 
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 捕获上传文件超出大小限制异常
     */
    // @ExceptionHandler(MaxUploadSizeExceededException.class)
    // public Result<?> handleUploadLimit(MaxUploadSizeExceededException e) {
    //     return Result.error("上传文件超出大小限制，最大支持50MB");
    // }
}