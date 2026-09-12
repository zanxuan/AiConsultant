package com.zx.consultant.chat.sse;

/**
 * 统一 SSE 事件。RAG / Summary / Chat 共用，错误也走同一结构。
 *
 * @param type    progress / complete / error
 * @param stage   当前阶段，如 rewrite、retrieve、generate、summary
 * @param message 给前端展示的文案
 * @param data    事件数据，没有时为 null
 */
public record SseEvent(
        String type,
        String stage,
        String message,
        Object data
) {
}
