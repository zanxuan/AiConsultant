package com.zx.consultant.trace.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次请求的完整 Trace 链路
 */
@Data
@Builder
public class TraceDetailResp {

    private String traceId;

    /** 节点执行链路（按执行顺序） */
    private List<SpanItem> spans;

    /** 各节点耗时合计（ms） */
    private Long totalCostTime;

    @Data
    @Builder
    public static class SpanItem {
        private Long id;
        private String nodeName;
        private Long costTime;
        private String status;
        private String errorMessage;
        private LocalDateTime createTime;
    }
}
