package com.zx.consultant.memory.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 从 Redis List 解析后的短期记忆视图
 */
@Data
public class ParsedMemory {

    /** 滚动摘要，可能为空 */
    private String summary;

    /** 窗口内的对话轮次（时间正序） */
    private final List<MemoryRound> rounds = new ArrayList<>();

    /**
     * 计算总字符数
     * @return 总字符数
     */
    public int totalChars() {
        int total = summary == null ? 0 : summary.length();
        for (MemoryRound round : rounds) {
            total += round.charCount();
        }
        return total;
    }

    public int roundCount() {
        return rounds.size();
    }
}
