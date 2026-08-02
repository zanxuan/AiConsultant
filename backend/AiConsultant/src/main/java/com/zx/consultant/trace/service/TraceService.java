package com.zx.consultant.trace.service;

import com.zx.consultant.common.trace.NodeSpan;
import com.zx.consultant.trace.dto.TraceDetailResp;

import java.util.List;

/**
 * Trace 查询与持久化
 */
public interface TraceService {

    /**
     * 将本次请求的节点 Span 写入 Redis
     */
    void saveSpans(String traceId, List<NodeSpan> nodeSpans);

    /**
     * 按 traceId 查询完整执行链路
     */
    TraceDetailResp getByTraceId(String traceId);
}
