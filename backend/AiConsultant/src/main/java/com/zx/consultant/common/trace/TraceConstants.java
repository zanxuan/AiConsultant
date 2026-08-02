package com.zx.consultant.common.trace;

/**
 * Trace 模块常量
 */
public final class TraceConstants {

    private TraceConstants() {
    }

    /** HTTP 请求/响应头中的 TraceId 名称 */
    public static final String HEADER_NAME = "X-Trace-Id";

    /** SLF4J MDC 中的 key，与 logback pattern 中 %X{traceId} 对应 */
    public static final String MDC_KEY = "traceId";
}
