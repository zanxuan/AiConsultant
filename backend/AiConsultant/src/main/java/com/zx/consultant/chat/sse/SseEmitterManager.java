package com.zx.consultant.chat.sse;

import com.zx.consultant.chat.dto.ChatResp;
import com.zx.consultant.common.trace.NodeSpan;
import com.zx.consultant.common.trace.TraceRecorder;
import com.zx.consultant.trace.service.TraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护 taskId → SseEmitter。连接结束时必须 remove，避免内存泄漏。
 */
@Slf4j
@Component
public class SseEmitterManager {

    /** LLM / RAG 可能超过默认 30s，这里给 SSE 5 分钟超时。 */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    /** SSE 是通信通道，失败只记这一条节点，不记成 Chat / LLM。 */
    private static final String SSE_NODE_NAME = "SSE";

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    /** Chat ask 的 traceId，不能用 stream() HTTP 线程上的 TraceContext。 */
    private final ConcurrentHashMap<String, String> taskTraceIds = new ConcurrentHashMap<>();
    /** 同一 task 的 send 失败常会再触发 onError，避免重复落库。 */
    private final ConcurrentHashMap<String, Boolean> sseFailureRecorded = new ConcurrentHashMap<>();

    private final TraceService traceService;

    public SseEmitterManager(TraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 绑定本次 Chat 请求的 traceId。必须在 ask() 线程写入，stream / 超时回调里 ThreadLocal 不可靠。
     */
    public void bindTraceId(String taskId, String traceId) {
        if (taskId == null || taskId.isBlank() || traceId == null || traceId.isBlank()) {
            return;
        }
        taskTraceIds.put(taskId, traceId);
    }

    public SseEmitter register(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 正常结束不记 SUCCESS，避免 Trace 噪音
        emitter.onCompletion(() -> remove(taskId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时, taskId={}", taskId);
            recordSseFailure(taskId, "SSE timeout", 0L);
            remove(taskId);
        });
        emitter.onError(ex -> {
            log.warn("SSE 连接异常, taskId={}", taskId, ex);
            recordSseFailure(taskId, sseErrorMessage(ex), 0L);
            remove(taskId);
        });
        emitters.put(taskId, emitter);
        return emitter;
    }

    public SseEmitter get(String taskId) {
        return emitters.get(taskId);
    }

    public void remove(String taskId) {
        if (taskId != null) {
            emitters.remove(taskId);
            taskTraceIds.remove(taskId);
            sseFailureRecorded.remove(taskId);
        }
    }

    /**
     * 尽力推送 progress，不关闭连接。emitter 尚未建立时直接丢弃。
     */
    public void sendProgress(String taskId, String message) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(message != null ? message : ""));
        } catch (Exception e) {
            recordSseFailure(taskId, sseErrorMessage(e), System.currentTimeMillis() - start);
            log.warn("SSE 推送 progress 失败, taskId={}", taskId, e);
            remove(taskId);
        }
    }

    /**
     * 尽力推送回答片段，不关闭连接。emitter 尚未建立时直接丢弃（与 progress 相同）。
     */
    public void sendAnswer(String taskId, String partialResponse) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            emitter.send(SseEmitter.event()
                    .name("answer")
                    .data(partialResponse != null ? partialResponse : ""));
        } catch (Exception e) {
            recordSseFailure(taskId, sseErrorMessage(e), System.currentTimeMillis() - start);
            log.warn("SSE 推送 answer 失败, taskId={}", taskId, e);
            remove(taskId);
        }
    }

    /**
     * 推送错误并关闭连接。emitter 尚未注册时由调用方把错误缓存在 ChatTask 上。
     */
    public void sendError(String taskId, String message) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            log.info("SSE 尚未连接，error 先留在 Task 上, taskId={}", taskId);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(message != null ? message : ""));
            emitter.complete();
        } catch (Exception e) {
            recordSseFailure(taskId, sseErrorMessage(e), System.currentTimeMillis() - start);
            log.warn("SSE 推送 error 失败, taskId={}", taskId, e);
            remove(taskId);
        }
    }

    /**
     * 推送最终 ChatResp 并关闭连接。emitter 尚未注册时由调用方把结果缓存在 ChatTask 上。
     */
    public void sendComplete(String taskId, ChatResp data) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            log.info("SSE 尚未连接，complete 结果先留在 Task 上, taskId={}", taskId);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(data));
            emitter.complete();
        } catch (Exception e) {
            recordSseFailure(taskId, sseErrorMessage(e), System.currentTimeMillis() - start);
            log.warn("SSE 推送 complete 失败, taskId={}", taskId, e);
            remove(taskId);
        }
    }

    /**
     * 独立落库 SSE FAILED，不写入 TraceContext，避免把通信失败算进 Workflow 节点。
     */
    private void recordSseFailure(String taskId, String errorMessage, long costMs) {
        try {
            String traceId = taskTraceIds.get(taskId);
            if (traceId == null || traceId.isBlank()) {
                return;
            }
            if (sseFailureRecorded.putIfAbsent(taskId, Boolean.TRUE) != null) {
                return;
            }
            NodeSpan span = TraceRecorder.failedSpan(traceId, SSE_NODE_NAME, errorMessage, costMs);
            traceService.saveSpans(traceId, List.of(span));
        } catch (Exception e) {
            log.warn("SSE FAILED Trace 落库失败, taskId={}", taskId, e);
        }
    }

    private static String sseErrorMessage(Throwable ex) {
        if (ex == null) {
            return "SSE error";
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}
