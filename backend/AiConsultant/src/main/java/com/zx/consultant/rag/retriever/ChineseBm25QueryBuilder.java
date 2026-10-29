package com.zx.consultant.rag.retriever;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按中文分词规则准备 BM25 查询串。
 * <p>
 * 索引侧使用 RediSearch {@code LANGUAGE chinese}（Friso）：标点是词边界，
 * 连续汉字再按词典切开，英文和数字保持原词。查询必须走同一套边界，
 * 否则整句会被当成一个英文词，中文词项对不上。
 * <p>
 * 标点切开之后，再用最长匹配把问句词标成 Redis 的可选词 {@code ~}。
 * 主题词仍连在一起，交给同一个中文分词器做 AND。单字只在它本身是最长匹配时才可选，
 * 避免把「所有」「没有」里的「有」拆出去。整句只剩问句词时返回空串，避免可选词匹配全部文档。
 */
public final class ChineseBm25QueryBuilder {

    /**
     * 中文标点以及 RediSearch 查询语法字符。连字符、加号、英文逗号也是词边界，
     * 不能转义后粘进词项（例如 {@code F\-08}、{@code B\+树}）。
     */
    private static final Pattern BOUNDARY = Pattern.compile(
            "[\\s,.<>{}\\[\\]\"':;!@#$%^&*()+=~|/\\\\?？！，。、；：·…—～（）【】《》「」『』“”‘’\\-]+");

    /** RediSearch 中文分词后的词项，带 (expanded) 的才是实际参与倒排的词 */
    private static final Pattern EXPANDED_TERM = Pattern.compile("(?:@text:)?([^\\s{}()]+)\\(expanded\\)");

    /**
     * 问句词和功能词。匹配时取最长的一条，整段标成可选。
     * 单字只作为最短候选，有更长的问句词或保留词时不会单独生效。
     */
    private static final String[] DROP_WORDS = {
            "有什么区别", "有哪几种", "是不是", "有哪些", "有什么", "有没有", "什么是", "是什么", "是如何",
            "哪几种", "为什么", "怎么办", "怎么样", "哪些", "哪种", "哪个", "哪里", "是否", "什么", "怎么",
            "怎样", "如何", "的", "是", "有", "和", "与", "或", "及", "吗", "呢", "吧", "啊"
    };

    /**
     * 本身含有问句字、但应整段保留的词。和问句词比长度，更长或相同则保留。
     */
    private static final String[] KEEP_WORDS = {
            "所有", "没有", "具有", "还有", "只有", "拥有", "含有", "带有", "持有",
            "有效", "有限", "有关", "有的", "有时", "有些", "有点", "有着", "有无",
            "目的", "但是", "可是", "还是", "就是", "要是", "或是", "是的", "不是", "总是",
            "之前", "之后", "以前", "以后", "然后", "前面", "后面", "其中",
            "以及", "或者", "并且", "而且", "任何"
    };

    private ChineseBm25QueryBuilder() {
    }

    /**
     * @param query 用户原问句
     * @return 空格分隔的查询片段；没有可用词时返回空串
     */
    public static String build(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean anyRequired = false;
        for (String token : BOUNDARY.split(query.trim())) {
            if (token.isEmpty()) {
                continue;
            }
            Marked marked = markQuestionWords(token);
            if (marked.text.isEmpty()) {
                continue;
            }
            if (marked.required) {
                anyRequired = true;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            // 边界已去掉语法字符，这里只兜底转义残留符号。~ 是可选词标记，不能转义
            sb.append(marked.text.replaceAll("([,.<>{}\\[\\]\"':;!@#$%^&*()\\-+=|/\\\\])", "\\\\$1"));
        }
        // 只有 ~词 时 RediSearch 会命中全部文档，这种问句不提交查询
        if (!anyRequired) {
            return "";
        }
        return sb.toString();
    }

    /**
     * 把 FT.EXPLAIN 里的中文词项改成 OR。词项顺序保留，重复词只留第一次。
     * 没有词项时返回空串，调用方继续用原来的查询。
     */
    public static String toOrQuery(String explainText) {
        if (explainText == null || explainText.isBlank()) {
            return "";
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = EXPANDED_TERM.matcher(explainText);
        while (matcher.find()) {
            String term = matcher.group(1);
            if (term != null && !term.isBlank()) {
                terms.add(term);
            }
        }
        return String.join(" | ", terms);
    }

    /**
     * 从左到右取当前位置最长的词。保留词优先于同长度的问句词。
     * 问句词写成 {@code ~词}，和前后主题词用空格隔开。
     */
    private static Marked markQuestionWords(String token) {
        StringBuilder out = new StringBuilder(token.length());
        boolean required = false;
        boolean separateNext = false;
        int i = 0;
        while (i < token.length()) {
            int keepLength = longestMatch(token, i, KEEP_WORDS);
            int optionalLength = longestMatch(token, i, DROP_WORDS);
            if (keepLength > 0 && keepLength >= optionalLength) {
                appendText(out, token.substring(i, i + keepLength), separateNext);
                separateNext = false;
                required = true;
                i += keepLength;
                continue;
            }
            if (optionalLength > 0) {
                appendText(out, "~" + token.substring(i, i + optionalLength), out.length() > 0);
                separateNext = true;
                i += optionalLength;
                continue;
            }
            appendText(out, String.valueOf(token.charAt(i)), separateNext);
            separateNext = false;
            required = true;
            i++;
        }
        return new Marked(out.toString().trim(), required);
    }

    private static void appendText(StringBuilder out, String text, boolean separate) {
        if (text.isEmpty()) {
            return;
        }
        if (separate && out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
        }
        out.append(text);
    }

    private static int longestMatch(String token, int offset, String[] words) {
        int best = 0;
        for (String word : words) {
            if (word.length() > best && token.startsWith(word, offset)) {
                best = word.length();
            }
        }
        return best;
    }

    private static final class Marked {
        private final String text;
        private final boolean required;

        private Marked(String text, boolean required) {
            this.text = text;
            this.required = required;
        }
    }
}
