package com.zx.consultant.memory.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis List 中单条元素的 JSON 结构（摘要 或 一轮对话）
 */
@Data
@NoArgsConstructor
public class MemoryItem {

    public static final String TYPE_SUMMARY = "summary";
    public static final String TYPE_ROUND = "round";

    /** summary | round */
    private String type;
    /** 摘要正文（type=summary 时使用） */
    private String content;
    /** 用户问题（type=round 时使用） */
    private String user;
    /** AI 回答（type=round 时使用） */
    private String assistant;

    public static MemoryItem summary(String content) {
        MemoryItem item = new MemoryItem();
        item.type = TYPE_SUMMARY;
        item.content = content;
        return item;
    }

    public static MemoryItem round(String user, String assistant) {
        MemoryItem item = new MemoryItem();
        item.type = TYPE_ROUND;
        item.user = user;
        item.assistant = assistant;
        return item;
    }

    public boolean isSummary() {
        return TYPE_SUMMARY.equals(type);
    }

    public boolean isRound() {
        return TYPE_ROUND.equals(type);
    }
}
