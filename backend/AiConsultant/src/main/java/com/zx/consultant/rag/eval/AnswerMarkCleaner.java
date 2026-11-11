package com.zx.consultant.rag.eval;

import java.util.regex.Pattern;

/**
 * 去掉答案里的机器标记，再交给裁判。
 * 标记范围与 {@code CitationNode} 一致，评分看的是用户实际会看到的正文。
 */
final class AnswerMarkCleaner {

    private static final Pattern MARKS = Pattern.compile(
            "<cite\\s+id=\"\\d+\"\\s*/?>|<reference_document\\s+id=\"\\d+\"[^>]*/?>|</reference_document>",
            Pattern.CASE_INSENSITIVE);

    private AnswerMarkCleaner() {
    }

    static String clean(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String cleaned = MARKS.matcher(answer).replaceAll("");
        cleaned = cleaned.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return cleaned;
    }
}
