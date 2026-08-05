package com.zx.consultant.workflow.node;

import com.zx.consultant.rag.dto.CitationDTO;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.service.CitationService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 引用提取节点
 * 负责解析大模型生成的回答，提取引用的文档到 references，
 * 并清洗 answer 正文中的引用标记，保证二者分离返回。
 * @author zx
 * @date 2026-07-20
 */
@Slf4j
@Component
@Order(6) // 紧随 LLMNode (Order 5) 之后执行
public class CitationNode implements WorkflowNode {

    /** 清洗 LLM 回答中用于追踪引用的机器标记，不展示给前端 */
    private static final Pattern CITE_MARK_PATTERN = Pattern.compile(
            "<cite\\s+id=\"\\d+\"\\s*/?>|<reference_document\\s+id=\"\\d+\"[^>]*/?>|</reference_document>",
            Pattern.CASE_INSENSITIVE);

    private final CitationService citationService;

    public CitationNode(CitationService citationService) {
        this.citationService = citationService;
    }

    @Override
    public void execute(WorkflowContext context) {
        log.info(">>> 执行节点: {} >>>", getName());

        // 1. 获取 LLM 生成的原始回答和检索到的文档
        String llmResponse = context.getLlmResponse();
        List<RetrievedChunk> docs = context.getRetrievedDocuments();

        // 基础校验：如果没有回答，直接跳过
        if (llmResponse == null || llmResponse.isBlank()) {
            log.info("回答为空，跳过引用提取。");
            return;
        }

        // 2. 先清洗正文：去掉 cite / reference_document 标记，answer 中不保留文件信息
        String cleanAnswer = cleanAnswer(llmResponse);
        context.setFinalAnswer(cleanAnswer);

        // 无检索资料则无法构建 references
        if (docs == null || docs.isEmpty()) {
            log.info("未检索到资料，仅返回清洗后的回答。");
            return;
        }

        // 3. 从原始回答提取 cite；若模型未打标，则用本次检索结果去重兜底（与 answer 分离）
        List<CitationDTO> citationList = citationService.extractCitations(llmResponse, docs);
        context.setCitations(citationList);

        if (!citationList.isEmpty()) {
            boolean hasCiteMark = llmResponse != null
                    && CITE_MARK_PATTERN.matcher(llmResponse).find();
            if (hasCiteMark) {
                log.info("成功提取 {} 个文献引用，answer / references 已分离。", citationList.size());
            } else {
                log.info("模型未打 cite 标记，已用检索结果兜底返回 {} 个引用。", citationList.size());
            }
        } else {
            log.info("未生成有效的文献引用。");
        }
    }

    /**
     * 去除回答中的机器引用标记，保留纯文本答案
     */
    private String cleanAnswer(String llmResponse) {
        String cleaned = CITE_MARK_PATTERN.matcher(llmResponse).replaceAll("");
        // 压缩因去标记产生的多余空行
        cleaned = cleaned.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return cleaned;
    }

    @Override
    public String getName() {
        return "Citation Extraction Node";
    }
}
