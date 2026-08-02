package com.zx.consultant.common.trace;

import lombok.extern.slf4j.Slf4j;

/**
 * Workflow 节点级 Trace 记录器。
 * <p>
 * 包装节点执行逻辑，统一采集耗时、状态与异常，写入 {@link TraceContext}。
 */
@Slf4j
public final class TraceRecorder {

    private TraceRecorder() {
    }

    /**
     * 执行并记录一个可抛出异常的节点逻辑。
     * 失败时记录 FAILED Span 后原样抛出异常，由上层决定是否中断。
     *
     * @param nodeName 节点名称
     * @param action   实际执行逻辑
     */
    public static void record(String nodeName, Runnable action) {
        String traceId = TraceContext.getTraceId();
        long start = System.currentTimeMillis();

        try {
            action.run();
            long end = System.currentTimeMillis();
            NodeSpan span = NodeSpan.builder()
                    .nodeName(nodeName)
                    .traceId(traceId)
                    .startTime(start)
                    .endTime(end)
                    .costMs(end - start)
                    .status(NodeStatus.SUCCESS)
                    .build();
            TraceContext.addSpan(span);
            log.info("[Trace] node={} cost={}ms status={}", nodeName, span.getCostMs(), span.getStatus());
        } catch (RuntimeException e) {
            long end = System.currentTimeMillis();
            NodeSpan span = NodeSpan.builder()
                    .nodeName(nodeName)
                    .traceId(traceId)
                    .startTime(start)
                    .endTime(end)
                    .costMs(end - start)
                    .status(NodeStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
            TraceContext.addSpan(span);
            log.error("[Trace] node={} cost={}ms status={} error={}",
                    nodeName, span.getCostMs(), span.getStatus(), span.getErrorMessage());
            throw e;
        }
    }

    /**
     * 打印当前请求的完整节点 Trace 摘要，便于排查瓶颈
     */
    public static void logSummary() {
        String traceId = TraceContext.getTraceId();
        // 使用 var 简写，编译器自动根据右侧返回值推断类型,只能用于方法内局部变量
        var spans = TraceContext.getSpans();
        if (spans.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== Trace Summary [traceId=").append(traceId).append("] ==========\n");
        for (NodeSpan span : spans) {
            sb.append(String.format("  %-28s cost=%5dms  status=%s",
                    span.getNodeName() + ":",
                    span.getCostMs(),
                    span.getStatus()));
            if (span.getErrorMessage() != null) {
                sb.append("  error=").append(span.getErrorMessage());
            }
            sb.append('\n');
        }
        long total = spans.stream().mapToLong(NodeSpan::getCostMs).sum();
        sb.append(String.format("  %-28s cost=%5dms\n", "TOTAL:", total));
        sb.append("========================================================");
        log.info(sb.toString());
    }
}
