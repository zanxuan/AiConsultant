package com.zx.consultant.common.trace;

import lombok.Builder;
import lombok.Data;

/**
 * 单个 Workflow 节点的 Trace 记录
 */
@Data
@Builder
public class NodeSpan {

    /** 节点名称 */
    private String nodeName;

    /** 所属请求的 traceId */
    private String traceId;

    /** 开始时间（epoch ms） */
    private long startTime;

    /** 结束时间（epoch ms） */
    private long endTime;

    /** 执行耗时（ms） */
    private long costMs;

    /** 执行状态 */
    private NodeStatus status;

    /** 失败时的异常信息，成功则为 null */
    private String errorMessage;
}
