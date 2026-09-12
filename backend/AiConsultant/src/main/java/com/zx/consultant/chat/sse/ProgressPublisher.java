package com.zx.consultant.chat.sse;

/**
 * 业务层进度发布接口。Workflow / Summary / Chat 只依赖本接口，不依赖 SseEmitter。
 */
public interface ProgressPublisher {

    void publish(SseEvent event);
}
