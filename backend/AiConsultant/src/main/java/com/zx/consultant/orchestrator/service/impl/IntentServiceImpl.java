package com.zx.consultant.orchestrator.service.impl;

import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.orchestrator.IntentResult;
import com.zx.consultant.orchestrator.enums.Intent;
import com.zx.consultant.orchestrator.service.IntentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 意图识别服务实现。只区分 RAG_QA、SUMMARY、CHAT，不判断总结范围。
 */
@Slf4j
@Service
public class IntentServiceImpl implements IntentService {

    private static final String SYSTEM_PROMPT = """
            你是一个 Intent 分类器。

            你的任务是根据用户输入，判断用户当前的主要意图，并从以下三个类别中选择一个：

            RAG_QA

            用户希望从企业知识库、已有文档或系统中的知识中获取信息。
            包括：查询、检索、定位、查找、询问某个知识点或事实。
            用户的问题需要通过知识库检索才能更好地回答时，选择 RAG_QA。
            询问知识库的主题、用途或有哪些内容，也选择 RAG_QA。
            例如：这个知识库主要是干什么的、这个知识库有哪些内容、这个知识库主要包含哪些方面。

            SUMMARY

            用户明确要求对已有文档或整个知识库做总结、归纳、提炼、概括、整理、提取重点。
            核心是“对已有内容进行总结”，而不是针对某个具体问题做知识问答。
            只是了解知识库是做什么的、有哪些内容，而没有要求总结时，选择 RAG_QA，不要选择 SUMMARY。

            CHAT

            用户进行一般性闲聊或普通对话。
            不需要查询企业知识库。
            不需要对文档或知识库进行总结。

            请只输出以下格式之一，不要输出其他内容：

            RAG_QA
            CHAT
            SUMMARY
            """;

    private final LLMService llmService;

    public IntentServiceImpl(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * 识别意图
     */
    @Override
    public IntentResult detect(String query) {
        if (query == null || query.isBlank()) {
            return new IntentResult(Intent.CHAT);
        }

        PromptRequest request = new PromptRequest();
        request.setSystemPrompt(SYSTEM_PROMPT);
        request.setUserQuery(query);
        request.setContext("");

        String raw = llmService.generateAnswer(request);
        IntentResult intentResult = new IntentResult(parse(raw));
        log.info("Intent 识别结果: {}, 模型原始输出: {}", intentResult, raw);
        return intentResult;
    }

    /**
     * 去掉模型输出中的噪音，只解析主意图。旧的 SUMMARY DOCUMENT 等输出仍按 SUMMARY 处理。
     */
    Intent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("Intent 模型输出为空，回退为 CHAT");
            return Intent.CHAT;
        }

        String label = raw.trim().split("\\R", 2)[0]
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("。", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        String intentLabel = label.split(" ")[0];
        if ("RAG".equals(intentLabel)) {
            intentLabel = "RAG_QA";
        }

        return switch (intentLabel) {
            case "RAG_QA" -> Intent.RAG_QA;
            case "SUMMARY" -> Intent.SUMMARY;
            case "CHAT" -> Intent.CHAT;
            default -> {
                // 模型若仍输出 SUMMARY_DOCUMENT 这类旧标签，主意图仍是总结
                if (intentLabel.startsWith("SUMMARY")) {
                    log.warn("Intent 输出带有已不再使用的总结范围，按 SUMMARY 处理: {}", raw);
                    yield Intent.SUMMARY;
                }
                log.warn("无法解析 Intent 输出，回退为 CHAT: {}", raw);
                yield Intent.CHAT;
            }
        };
    }
}
