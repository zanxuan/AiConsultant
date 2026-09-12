package com.zx.consultant.orchestrator.service.impl;

import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.orchestrator.service.ClarificationReplyClassifier;
import com.zx.consultant.pending.model.PendingTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 先做廉价硬判断（文件名/序号），不确定时复用问答 LLM 判别，避免把新问题继续锁在 Summary 上。
 */
@Slf4j
@Service
public class ClarificationReplyClassifierImpl implements ClarificationReplyClassifier {

    private static final Pattern FILE_EXTENSION = Pattern.compile(
            "\\.(pdf|md|txt|markdown)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern QUESTION_HINT = Pattern.compile(
            "[?？]|怎么|如何|为什么|什么是|哪[里儿]|知识库");

    private static final String SYSTEM_PROMPT = """
            你是一个澄清回复判别器。

            当前会话存在一个尚未完成的短任务：用户想总结某篇文档，系统请用户指定文件名。
            这不是会话模式，只判断这一次输入。

            判断用户最新输入属于哪一类：
            ANSWER：用户在指定或描述要总结的文档，包括文件名、序号、简称、口语、轻微错别字。
            NEW_QUESTION：用户提出了与“指定要总结哪篇文档”无关的新问题，包括知识问答、闲聊、总结整个知识库。

            只返回以下之一，不要解释：
            ANSWER
            NEW_QUESTION
            """;

    private final LLMService llmService;

    public ClarificationReplyClassifierImpl(LLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    public boolean isAnsweringClarification(PendingTask pending, String currentInput) {
        if (currentInput == null || currentInput.isBlank() || pending == null) {
            return false;
        }
        String input = currentInput.trim();
        List<String> candidates = pending.getCandidateFileNames();

        // 文件名、主干或列表序号明显对应候选文档时，不再调用模型。
        if (looksLikeDocumentAnswer(input, candidates)) {
            log.info("澄清回复硬判断为 ANSWER, input={}", input);
            return true;
        }

        try {
            PromptRequest request = new PromptRequest();
            request.setSystemPrompt(SYSTEM_PROMPT);
            request.setContext(buildContext(pending));
            request.setUserQuery(input);
            String raw = llmService.generateAnswer(request);
            boolean answering = parseAnswering(raw);
            log.info("澄清回复模型判断: {}, 原始输出: {}", answering ? "ANSWER" : "NEW_QUESTION", raw);
            return answering;
        } catch (Exception e) {
            // 模型失败时：短句且不像问句则当作回答，避免把“项目设计”误送去闲聊。
            boolean fallback = !looksLikeNewQuestion(input);
            log.warn("澄清回复判别失败，回退为 {}, input={}", fallback ? "ANSWER" : "NEW_QUESTION", input, e);
            return fallback;
        }
    }

    private boolean looksLikeDocumentAnswer(String input, List<String> candidates) {
        if (matchesCandidateIndex(input, candidates)) {
            return true;
        }
        if (candidates != null) {
            String normalized = input.toLowerCase(Locale.ROOT);
            for (String fileName : candidates) {
                if (fileName == null || fileName.isBlank()) {
                    continue;
                }
                String name = fileName.toLowerCase(Locale.ROOT).trim();
                if (normalized.contains(name)) {
                    return true;
                }
                int dot = name.lastIndexOf('.');
                String stem = dot > 0 ? name.substring(0, dot) : name;
                if (stem.length() >= 2 && normalized.contains(stem)) {
                    return true;
                }
            }
        }
        return FILE_EXTENSION.matcher(input).find();
    }

    private static boolean matchesCandidateIndex(String input, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        if (input.matches("\\d+")) {
            int idx = Integer.parseInt(input);
            return idx >= 1 && idx <= candidates.size();
        }
        if (input.matches("[A-Za-z]")) {
            int idx = Character.toUpperCase(input.charAt(0)) - 'A';
            return idx >= 0 && idx < candidates.size();
        }
        return false;
    }

    private static boolean looksLikeNewQuestion(String input) {
        return input.length() > 40 || QUESTION_HINT.matcher(input).find();
    }

    private static String buildContext(PendingTask pending) {
        StringBuilder context = new StringBuilder();
        context.append("原始总结请求：\n")
                .append(pending.getOriginalQuery() == null ? "" : pending.getOriginalQuery())
                .append("\n\n候选文档：\n");
        List<String> candidates = pending.getCandidateFileNames();
        if (candidates == null || candidates.isEmpty()) {
            context.append("（无）");
            return context.toString();
        }
        for (int i = 0; i < candidates.size(); i++) {
            context.append(i + 1).append(". ").append(candidates.get(i)).append("\n");
        }
        return context.toString();
    }

    private static boolean parseAnswering(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String label = raw.trim().split("\\R", 2)[0]
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        if (label.contains("NEW_QUESTION") || label.contains("NEW QUESTION")) {
            return false;
        }
        return label.contains("ANSWER");
    }
}
