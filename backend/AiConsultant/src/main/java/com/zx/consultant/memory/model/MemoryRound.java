package com.zx.consultant.memory.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一轮对话：用户问题 + AI 回答
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRound {

    private String user;
    private String assistant;

    public int charCount() {
        return length(user) + length(assistant);
    }

    private static int length(String s) {
        return s == null ? 0 : s.length();
    }
}
