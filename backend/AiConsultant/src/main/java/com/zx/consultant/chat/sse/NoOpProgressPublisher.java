package com.zx.consultant.chat.sse;

/**
 * 无 SSE 订阅时的空实现，保证评测/单测直接跑 Workflow 时不必注入 Web 层。
 */
public enum NoOpProgressPublisher implements ProgressPublisher {

    INSTANCE;

    @Override
    public void publish(SseEvent event) {
        // 非任务链路不推送
    }
}
