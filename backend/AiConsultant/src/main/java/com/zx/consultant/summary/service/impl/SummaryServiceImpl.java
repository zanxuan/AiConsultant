package com.zx.consultant.summary.service.impl;

import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.summary.config.SummaryProperties;
import com.zx.consultant.summary.service.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 总结执行器实现：短文档对原文一次总结，长文档复用已入库 Chunk 按 group-size 分组归纳。
 * 不负责切分文本，也不负责判断用户意图。
 */
@Slf4j
@Service
public class SummaryServiceImpl implements SummaryService {

    /** 短文本或未切块时的系统提示：直接产出完整文档总结。 */
    private static final String DOCUMENT_SYSTEM_PROMPT = """
            你是文档总结助手。

            请根据给定文档内容进行总结。

            要求：
            1. 只依据给定内容，不要编造文档中不存在的信息。
            2. 提炼主题、核心观点和关键要点。
            3. 表达清晰、结构完整。
            """;

    /** 长文本分组后的系统提示：只做阶段性归纳，不写成终稿。 */
    private static final String PARTIAL_SYSTEM_PROMPT = """
            你是文档总结助手。

            请对给定的文档片段做阶段性归纳。

            要求：
            1. 只依据给定片段，不要编造信息。
            2. 保留关键事实、结论和重要细节。
            3. 不要展开成最终成稿，为后续综合总结做准备。
            """;

    /** 综合各阶段摘要时的系统提示：覆盖全文、避免重复堆砌。 */
    private static final String FINAL_SYSTEM_PROMPT = """
            你是文档总结助手。

            下面是一篇文档的阶段性摘要，请综合生成最终总结。

            要求：
            1. 只依据阶段性摘要，不要编造新的事实。
            2. 覆盖各阶段的核心内容，避免重复堆砌。
            3. 输出完整、结构清晰的最终总结。
            """;

    /** 知识库级总结的系统提示：依据各文档摘要做主题归类，不罗列原文。 */
    private static final String KNOWLEDGE_BASE_SYSTEM_PROMPT = """
            你是知识库总结助手。

            下面是该知识库中各篇已就绪文档的摘要，请生成知识库级的整体概括。

            要求：
            1. 只依据这些文档摘要，不要编造知识库中不存在的内容。
            2. 说明知识库的主题、覆盖范围和主要内容。
            3. 可以按主题归类，不要简单罗列原文。
            """;

    private final LLMService llmService;
    private final SummaryProperties summaryProperties;

    public SummaryServiceImpl(LLMService llmService, SummaryProperties summaryProperties) {
        this.llmService = llmService;
        this.summaryProperties = summaryProperties;
    }

    /**
     * 对短文档原文做一次总结，不再加载或切分 Chunk。
     */
    @Override
    public String summarizeDocumentText(String text, String userQuery) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("文档内容为空");
        }
        log.info("短文档直接总结, length={}", text.length());
        return callLlm(DOCUMENT_SYSTEM_PROMPT, text.trim(),
                defaultUserQuery(userQuery, "请总结以下文档。"));
    }

    /**
     * 对长文档做总结：复用已入库 Chunk，按 group-size 分组归纳后再综合。
     */
    @Override
    public String summarizeDocumentText(List<String> chunks, String userQuery) {
        return summarizeByExistingChunks(chunks, userQuery, DOCUMENT_SYSTEM_PROMPT);
    }

    /**
     * 将各文档摘要聚合成知识库级最终总结，不再对原文二次切块。
     */
    @Override
    public String summarizeKnowledgeBase(String aggregatedDocumentSummaries, String userQuery) {
        return callLlm(KNOWLEDGE_BASE_SYSTEM_PROMPT, aggregatedDocumentSummaries, userQuery);
    }

    /**
     * 复用文档处理层已生成的 Chunk，按 group-size 分组阶段性归纳后再综合。
     */
    private String summarizeByExistingChunks(List<String> rawChunks, String userQuery, String directSystemPrompt) {
        // 去掉空块，保留文档处理层给出的切块顺序。
        List<String> chunks = normalizeChunks(rawChunks);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文档内容为空");
        }

        int totalLength = 0;
        for (String chunk : chunks) {
            totalLength += chunk.length();
        }

        // 仅一块时无需分组，直接总结。
        if (chunks.size() <= 1) {
            log.info("长文档仅一块，直接总结, length={}", totalLength);
            return callLlm(directSystemPrompt, joinChunks(chunks),
                    defaultUserQuery(userQuery, "请总结以下文档。"));
        }

        log.info("复用已有切块分组总结, length={}, chunkCount={}", totalLength, chunks.size());
        // 按 groupSize 合并相邻已有块，每组生成一篇阶段性摘要，控制单次上下文长度。
        int groupSize = Math.max(1, summaryProperties.getGroupSize());
        List<String> partials = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += groupSize) {
            // 获取当前块的结束索引
            int end = Math.min(i + groupSize, chunks.size());
            // 获取当前块的子列表
            String group = joinChunks(chunks.subList(i, end));
            // 生成阶段性总结
            String partial = callLlm(PARTIAL_SYSTEM_PROMPT, group, "请对以上内容进行阶段性总结。");
            if (partial != null && !partial.isBlank()) {
                // 如果阶段性总结不为空，则添加到列表中
                partials.add(partial);
            }
        }
        if (partials.isEmpty()) {
            throw new IllegalStateException("阶段性总结结果为空");
        }

        // 带序号拼接各阶段摘要，便于最终总结覆盖全文且减少重复。
        StringBuilder aggregated = new StringBuilder();
        for (int i = 0; i < partials.size(); i++) {
            if (i > 0) {
                aggregated.append("\n\n");
            }
            aggregated.append("【阶段性总结 ").append(i + 1).append("】\n").append(partials.get(i));
        }
        return callLlm(FINAL_SYSTEM_PROMPT, aggregated.toString(),
                defaultUserQuery(userQuery, "请基于以上阶段性总结生成最终总结。"));
    }

    /** 去掉空块，保留文档处理层给出的切块顺序。 */
    private static List<String> normalizeChunks(List<String> rawChunks) {
        List<String> chunks = new ArrayList<>();
        if (rawChunks == null) {
            return chunks;
        }
        for (String chunk : rawChunks) {
            if (chunk != null && !chunk.isBlank()) {
                chunks.add(chunk.trim());
            }
        }
        return chunks;
    }

    private static String joinChunks(List<String> chunks) {
        return String.join("\n\n", chunks);
    }

    /** 组装提示词并调用模型；空结果视为失败，避免把空白当作有效总结。 */
    private String callLlm(String systemPrompt, String context, String userQuery) {
        PromptRequest request = new PromptRequest();
        request.setSystemPrompt(systemPrompt);
        request.setContext(context == null ? "" : context);
        request.setUserQuery(userQuery);
        String answer = llmService.generateAnswer(request);
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("模型未返回总结结果");
        }
        return answer;
    }

    /** 用户问句为空时使用场景默认提示，否则保留原问句以贴近用户意图。 */
    private static String defaultUserQuery(String userQuery, String fallback) {
        return userQuery == null || userQuery.isBlank() ? fallback : userQuery;
    }
}
