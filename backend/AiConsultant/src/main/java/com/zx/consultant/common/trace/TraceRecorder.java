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
            boolean llmSpan = TraceContext.getModelUsed() != null
                    || TraceContext.isFallbackTriggered()
                    || TraceContext.getFallbackReason() != null;
            NodeSpan span = NodeSpan.builder()
                    .nodeName(nodeName)
                    .traceId(traceId)
                    .startTime(start)
                    .endTime(end)
                    .costMs(end - start)
                    .status(NodeStatus.SUCCESS)
                    .modelUsed(TraceContext.getModelUsed())
                    .fallbackTriggered(llmSpan ? TraceContext.isFallbackTriggered() : null)
                    .fallbackReason(TraceContext.getFallbackReason())
                    .build();
            TraceContext.addSpan(span);
            clearLlmTraceFieldsIfPresent(span);
            log.info("[Trace] node={} cost={}ms status={} model={} fallback={}",
                    nodeName, span.getCostMs(), span.getStatus(),
                    span.getModelUsed(), span.getFallbackTriggered());
                    
        } catch (RuntimeException e) {
            long end = System.currentTimeMillis();
            boolean llmSpan = TraceContext.getModelUsed() != null
                    || TraceContext.isFallbackTriggered()
                    || TraceContext.getFallbackReason() != null;
            NodeSpan span = NodeSpan.builder()
                    .nodeName(nodeName)
                    .traceId(traceId)
                    .startTime(start)
                    .endTime(end)
                    .costMs(end - start)
                    .status(NodeStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .modelUsed(TraceContext.getModelUsed())
                    .fallbackTriggered(llmSpan ? TraceContext.isFallbackTriggered() : null)
                    .fallbackReason(TraceContext.getFallbackReason())
                    .build();
            TraceContext.addSpan(span);
            clearLlmTraceFieldsIfPresent(span);
            log.error("[Trace] node={} cost={}ms status={} error={} model={} fallback={}",
                    nodeName, span.getCostMs(), span.getStatus(), span.getErrorMessage(),
                    span.getModelUsed(), span.getFallbackTriggered());
            throw e;
        }
    }

    /** LLM 字段只落在产生它们的那个 Span 上，避免后续节点误继承 */
    private static void clearLlmTraceFieldsIfPresent(NodeSpan span) {
        if (span.getModelUsed() == null
                && span.getFallbackTriggered() == null
                && span.getFallbackReason() == null) {
            return;
        }
        TraceContext.setModelUsed(null);
        TraceContext.setFallbackTriggered(false);
        TraceContext.setFallbackReason(null);
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
