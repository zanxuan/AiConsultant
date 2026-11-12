package com.zx.consultant.orchestrator.service.impl;

import com.zx.consultant.common.trace.TraceRecorder;
import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.orchestrator.enums.SummaryTool;
import com.zx.consultant.orchestrator.service.SummaryPlanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 总结工具选择：一次非流式调用，只输出工具名。文件名匹配和澄清仍由单文档总结完成。
 */
@Slf4j
@Service
public class SummaryPlannerImpl implements SummaryPlanner {

    private static final String SYSTEM_PROMPT = """
            你是总结工具选择器。用户的请求已经被判定为总结，你只负责选择一个工具，不要回答用户。

            summarize_document
            用户希望总结某一篇文档，或只说“总结一下”“帮我总结”但没有明确说整个知识库。
            即使用户没有给出文件名，也选择这个工具。

            summarize_knowledge_base
            用户明确要求概括、总结、归纳当前整个知识库的全部内容。
            典型说法：总结整个知识库、概括一下这个知识库、把知识库整体归纳一下。
            只是询问知识库是做什么的、有哪些内容时，不要选择这个工具。

            请只输出以下之一，不要输出其他内容：

            summarize_document
            summarize_knowledge_base
            """;

    private final LLMService llmService;

    public SummaryPlannerImpl(LLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    public SummaryTool choose(String query) {
        if (query == null || query.isBlank()) {
            log.info("总结问题为空，回退为 summarize_document");
            return SummaryTool.SUMMARIZE_DOCUMENT;
        }

        // TraceRecorder 不返回值，用数组把解析结果带出记录范围
        SummaryTool[] selected = new SummaryTool[1];
        TraceRecorder.record("Summary Tool Select", () -> {
            PromptRequest request = new PromptRequest();
            request.setSystemPrompt(SYSTEM_PROMPT);
            request.setUserQuery(query);
            request.setContext("");
            String raw = llmService.generateAnswer(request);
            selected[0] = parse(raw);
            log.info("总结 tool 选择: {}, 模型原始输出: {}", selected[0], raw);
        });
        return selected[0];
    }

    /**
     * 只认开头的工具名。整库总结必须明确以知识库工具开头，其余无法识别的输出回退单文档。
     */
    SummaryTool parse(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("总结 tool 输出为空，回退为 summarize_document");
            return SummaryTool.SUMMARIZE_DOCUMENT;
        }

        String label = raw.trim().split("\\R", 2)[0]
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("。", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        // 以开头的工具名为准，后面再出现 knowledge 也不改判
        if (label.startsWith("SUMMARIZE_DOCUMENT") || label.startsWith("DOCUMENT")) {
            return SummaryTool.SUMMARIZE_DOCUMENT;
        }
        if (label.startsWith("SUMMARIZE_KNOWLEDGE_BASE") || label.startsWith("KNOWLEDGE")) {
            return SummaryTool.SUMMARIZE_KNOWLEDGE_BASE;
        }
        if (label.startsWith("SUMMARY") || label.startsWith("SUMMARIZE")) {
            if (label.contains("KNOWLEDGE")) {
                return SummaryTool.SUMMARIZE_KNOWLEDGE_BASE;
            }
            return SummaryTool.SUMMARIZE_DOCUMENT;
        }
        log.warn("无法解析总结 tool，回退为 summarize_document: {}", raw);
        return SummaryTool.SUMMARIZE_DOCUMENT;
    }
}
