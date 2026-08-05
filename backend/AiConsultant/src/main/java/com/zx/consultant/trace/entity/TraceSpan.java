package com.zx.consultant.trace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow 节点级 Trace 持久化实体，对应表 trace_span
 */
@Data
@TableName("trace_span")
public class TraceSpan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private String nodeName;

    /** 执行耗时（ms） */
    private Long costTime;

    /** SUCCESS / FAILED */
    private String status;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private String modelUsed;

    private Boolean fallbackTriggered;

    private String fallbackReason;
}
