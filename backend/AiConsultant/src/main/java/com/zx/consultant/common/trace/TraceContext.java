package com.zx.consultant.common.trace;

import org.slf4j.MDC;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 请求级 Trace 上下文（ThreadLocal）。
 * <p>
 * 负责持有当前请求的 traceId 与节点级 Span 列表。
 * 由 {@link TraceIdFilter} 在请求入口初始化，在请求结束时清理。
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    //调用 .get() 的时候，如果该线程还没有绑定数据，自动执行新建一个空 ArrayList 返回
    //每个线程，拥有独立一份 List<NodeSpan>，线程之间互不干扰
    private static final ThreadLocal<List<NodeSpan>> SPANS = ThreadLocal.withInitial(ArrayList::new);
    /** 本次请求实际产出回答的模型名 */
    private static final ThreadLocal<String> MODEL_USED = new ThreadLocal<>();
    /** 本次请求是否触发了主模型 → 副模型降级 */
    private static final ThreadLocal<Boolean> FALLBACK_TRIGGERED = ThreadLocal.withInitial(() -> false);
    /** 降级原因，如 PRIMARY_LLM_FAILED */
    private static final ThreadLocal<String> FALLBACK_REASON = new ThreadLocal<>();

    private TraceContext() {
    }

    /**
     * 初始化当前请求的 Trace：写入 ThreadLocal + MDC。
     *
     * @param incomingTraceId 客户端传入的 traceId，为空则自动生成
     * @return 最终使用的 traceId
     */
    public static String init(String incomingTraceId) {
        String traceId = (incomingTraceId == null || incomingTraceId.isBlank())
                ? generateTraceId()
                : incomingTraceId.trim();
        TRACE_ID.set(traceId);
        SPANS.set(new ArrayList<>());
        MODEL_USED.remove();
        FALLBACK_TRIGGERED.set(false);
        FALLBACK_REASON.remove();
        //MDC 是专门给日志系统用的 ThreadLocal，把 traceId 写入 MDC，方便后续日志打印时带上 traceId
        MDC.put(TraceConstants.MDC_KEY, traceId);
        return traceId;
    }

    public static void setModelUsed(String modelUsed) {
        MODEL_USED.set(modelUsed);
    }

    public static String getModelUsed() {
        return MODEL_USED.get();
    }

    public static void setFallbackTriggered(boolean triggered) {
        FALLBACK_TRIGGERED.set(triggered);
    }

    public static boolean isFallbackTriggered() {
        Boolean value = FALLBACK_TRIGGERED.get();
        return value != null && value;
    }

    public static void setFallbackReason(String reason) {
        FALLBACK_REASON.set(reason);
    }

    public static String getFallbackReason() {
        return FALLBACK_REASON.get();
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    /**
     * 记录一个节点 Span
     */
    public static void addSpan(NodeSpan span) {
        List<NodeSpan> spans = SPANS.get();
        if (spans != null) {
            spans.add(span);
        }
    }

    /**
     * 获取当前请求已记录的全部节点 Span（只读）
     */
    public static List<NodeSpan> getSpans() {
        List<NodeSpan> spans = SPANS.get();
        if (spans == null || spans.isEmpty()) {
            return Collections.emptyList();
        }
        // 返回一个不可修改的列表，防止外部修改
        return Collections.unmodifiableList(spans);
    }

    /**
     * 清理 ThreadLocal 与 MDC，防止线程池复用导致串扰
     */
    public static void clear() {
        TRACE_ID.remove();
        SPANS.remove();
        MODEL_USED.remove();
        FALLBACK_TRIGGERED.remove();
        FALLBACK_REASON.remove();
        MDC.remove(TraceConstants.MDC_KEY);
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
