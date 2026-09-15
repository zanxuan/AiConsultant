package com.zx.consultant.chat.sse;

import com.zx.consultant.chat.dto.ChatResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护 taskId → SseEmitter。连接结束时必须 remove，避免内存泄漏。
 */
@Slf4j
@Component
public class SseEmitterManager {

    /** LLM / RAG 可能超过默认 30s，这里给 SSE 5 分钟超时。 */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> remove(taskId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时, taskId={}", taskId);
            remove(taskId);
        });
        emitter.onError(ex -> {
            log.warn("SSE 连接异常, taskId={}", taskId, ex);
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
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(message != null ? message : "", MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            log.warn("SSE 推送 progress 失败, taskId={}", taskId, e);
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
        try {
            emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(data));
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE 推送 complete 失败, taskId={}", taskId, e);
            remove(taskId);
        }
    }
}
