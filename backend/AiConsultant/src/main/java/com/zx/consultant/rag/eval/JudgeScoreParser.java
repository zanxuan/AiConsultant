package com.zx.consultant.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 解析裁判返回的 {@code {"score": 0.85, "reason": "..."}}。
 * 允许外面包一层说明或 Markdown 代码块，分数必须落在 0 到 1。
 */
final class JudgeScoreParser {

    private final ObjectMapper objectMapper;

    JudgeScoreParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JudgeScore parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("裁判返回为空");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(extractObject(raw));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("裁判返回不是 JSON 对象");
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("裁判返回不是 JSON 对象");
        }
        return new JudgeScore(readScore(node), readReason(node));
    }

    private static String extractObject(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstBreak = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstBreak >= 0 && lastFence > firstBreak) {
                text = text.substring(firstBreak + 1, lastFence).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("裁判返回不是 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    private static double readScore(JsonNode node) {
        JsonNode scoreNode = node.get("score");
        if (scoreNode == null || scoreNode.isNull()) {
            throw new IllegalArgumentException("裁判返回缺少 score");
        }
        double score;
        if (scoreNode.isNumber()) {
            score = scoreNode.doubleValue();
        } else if (scoreNode.isTextual()) {
            try {
                score = Double.parseDouble(scoreNode.asText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("裁判 score 不是数字");
            }
        } else {
            throw new IllegalArgumentException("裁判 score 不是数字");
        }
        if (Double.isNaN(score) || score < 0d || score > 1d) {
            throw new IllegalArgumentException("裁判 score 超出 0 到 1");
        }
        return score;
    }

    private static String readReason(JsonNode node) {
        JsonNode reasonNode = node.get("reason");
        if (reasonNode == null || reasonNode.isNull()) {
            return "";
        }
        if (!reasonNode.isTextual()) {
            throw new IllegalArgumentException("裁判 reason 必须是字符串");
        }
        return reasonNode.asText().trim();
    }

    record JudgeScore(double score, String reason) {
    }
}
