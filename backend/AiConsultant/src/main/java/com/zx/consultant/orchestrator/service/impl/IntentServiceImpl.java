package com.zx.consultant.orchestrator.service.impl;

import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.orchestrator.IntentResult;
import com.zx.consultant.orchestrator.enums.Intent;
import com.zx.consultant.orchestrator.enums.SummaryScope;
import com.zx.consultant.orchestrator.service.IntentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 意图识别服务实现
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

            SUMMARY

            用户希望对文档或知识库进行总结。
            包括：总结、归纳、提炼、概括、整理、提取重点。
            核心是“对已有内容进行总结”，而不是针对某个具体问题做知识问答。

            当意图为 SUMMARY 时，还必须判断总结范围：
            DOCUMENT：总结某一篇文档。用户明确指向某一份文件、某篇文章、某个文档。
            KNOWLEDGE_BASE：总结整个知识库。用户希望概括当前知识库的整体内容。
            KNOWLEDGE_BASE 的典型问法包括：这个知识库主要是干什么的、这个知识库有哪些内容、概括一下这个知识库、这个知识库主要包含哪些方面。

            CHAT

            用户进行一般性闲聊或普通对话。
            不需要查询企业知识库。
            不需要对文档或知识库进行总结。

            请只输出以下格式之一，不要输出其他内容：

            RAG_QA
            CHAT
            SUMMARY DOCUMENT
            SUMMARY KNOWLEDGE_BASE
            """;

    private final LLMService llmService;

    public IntentServiceImpl(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * 识别意图
     * @param query
     * @return
     */
    @Override
    public IntentResult detect(String query) {
        if (query == null || query.isBlank()) {
            return new IntentResult(Intent.CHAT, null);
        }

        PromptRequest request = new PromptRequest();
        request.setSystemPrompt(SYSTEM_PROMPT);
        request.setUserQuery(query);
        request.setContext("");

        String raw = llmService.generateAnswer(request);
        IntentResult intentResult = parse(raw);
        log.info("Intent 识别结果: {}, 模型原始输出: {}", intentResult, raw);
        return intentResult;
    }

    /**
     * 去掉模型输出中的噪音，解析意图及总结范围
     * @param raw
     * @return
     */
    private IntentResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("Intent 模型输出为空，回退为 CHAT");
            return new IntentResult(Intent.CHAT, null);
        }

        String label = raw.trim().split("\\R", 2)[0]
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("。", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        //以空格分割标签
        String[] parts = label.split(" ");
        String intentLabel = parts[0];
        if ("RAG".equals(intentLabel)) {
            intentLabel = "RAG_QA";
        }

        Intent intent = switch (intentLabel) {
            case "RAG_QA" -> Intent.RAG_QA;
            case "SUMMARY" -> Intent.SUMMARY;
            case "CHAT" -> Intent.CHAT;
            default -> {
                log.warn("无法解析 Intent 输出，回退为 CHAT: {}", raw);
                yield Intent.CHAT;
            }
        };

    

        //处理 SUMMARY 的子范围 SummaryScope
        SummaryScope summaryScope = null;
        if (intent == Intent.SUMMARY) {
            String scopeLabel = parts.length > 1 ? parts[1] : "";
            if (scopeLabel.startsWith("KNOWLEDGE")) {
                summaryScope = SummaryScope.KNOWLEDGE_BASE;
            } else {
                if (!"DOCUMENT".equals(scopeLabel)) {
                    log.warn("无法解析 SummaryScope，回退为 DOCUMENT: {}", raw);
                }
                summaryScope = SummaryScope.DOCUMENT;
            }
        }

        return new IntentResult(intent, summaryScope);
    }
}
