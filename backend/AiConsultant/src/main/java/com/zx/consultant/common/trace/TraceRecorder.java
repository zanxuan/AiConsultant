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
        // Chat SSE 链路才发 progress；失败不影响后续 Trace / Workflow
        emitProgress(nodeName);

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

    /**
     * 构造通信层 FAILED Span，不执行业务、不抛异常、不写入 TraceContext。
     * 供 SSE 等通道失败单独落库，避免打断 Chat / RAG / LLM。
     */
    public static NodeSpan failedSpan(String traceId, String nodeName, String errorMessage, long costMs) {
        long end = System.currentTimeMillis();
        long cost = Math.max(0L, costMs);
        return NodeSpan.builder()
                .nodeName(nodeName)
                .traceId(traceId)
                .startTime(end - cost)
                .endTime(end)
                .costMs(cost)
                .status(NodeStatus.FAILED)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 有 taskId 时尽力推送 SSE progress；无 taskId（评测/同步）或发送失败都不打断主流程。
     */
    private static void emitProgress(String nodeName) {
        String taskId = TraceContext.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        // 获取进度推送的接收器（就是前面说的ProgressPublisher/SSE发送器）
        var sink = TraceContext.getProgressSink();
        if (sink == null) {
            return;
        }
        try {
            // 组装进度消息，交给sink推送出去（SSE前端收到进度事件）
            sink.accept(progressMessage(nodeName));
            log.info("-------------------------------------------------------------------------------------------------------SSE progress 触发成功, taskId={}, nodeName={}", taskId, nodeName);
        } catch (Exception e) {
            log.warn("SSE progress 触发失败, taskId={}, nodeName={}", taskId, nodeName, e);
        }
    }

    /** 文案按实际 TraceRecorder / node.getName() 映射，未知名称用通用提示。 */
    private static String progressMessage(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) {
            return "正在处理...";
        }
        return switch (nodeName) {
            case "Memory Load" -> "正在读取对话上下文...";
            case "Query Rewrite Node" -> "正在分析问题...";
            case "Retrieve Node" -> "正在检索相关文档...";
            case "Prompt Builder Node" -> "正在整理相关内容...";
            case "LLM Generation Node" -> "正在生成回答...";
            case "Citation Extraction Node" -> "正在整理引用...";
            case "Memory Persist" -> "正在保存对话...";
            default -> "正在处理：" + nodeName;
        };
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
