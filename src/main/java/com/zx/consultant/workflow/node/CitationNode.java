package com.zx.consultant.workflow.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.rag.dto.CitationDTO;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.service.CitationService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 引用提取节点
 * 负责解析大模型生成的回答，提取引用的文档，并回写到上下文中
 * @author zx
 * @date 2026-07-20
 */
@Slf4j
@Component
@Order(6) // 紧随 LLMNode (Order 5) 之后执行
public class CitationNode implements WorkflowNode {

    private final CitationService citationService;
    private final ObjectMapper objectMapper; // Spring Boot 默认配置了 Jackson ObjectMapper

    public CitationNode(CitationService citationService, ObjectMapper objectMapper) {
        this.citationService = citationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(WorkflowContext context) {
        log.info(">>> 执行节点: {} >>>", getName());

        // 1. 获取 LLM 生成的原始回答和检索到的文档
        String llmResponse = context.getLlmResponse();
        List<RetrievedChunk> docs = context.getRetrievedDocuments();

        // 基础校验：如果没有回答或者没查到资料，直接跳过
        if (llmResponse == null || llmResponse.isBlank() || docs == null || docs.isEmpty()) {
            log.info("回答为空或未检索到资料，跳过引用提取。");
            return;
        }

        // 2. 调用 CitationService 提取结构化引用列表
        List<CitationDTO> citationList = citationService.extractCitations(llmResponse, docs);

        // 3. 将引用结果保存到上下文
        if (!citationList.isEmpty()) {
            try {
                // 因为 WorkflowContext 里的 citations 是 String 类型，这里将其转为 JSON 字符串
                // 提示：你也可以考虑直接将 WorkflowContext 里的字段类型改为 List<CitationDTO>，就省去了序列化步骤
                String citationsJson = objectMapper.writeValueAsString(citationList);
                context.setCitations(citationsJson);
                
                log.info("成功提取 {} 个文献引用并存入 Context。", citationList.size());
            } catch (JsonProcessingException e) {
                log.error("引用列表 JSON 序列化失败", e);
            }

            // ==========================================
            // [可选增强] 将引用列表直接格式化追加到最终文本末尾
            // 这样前端就不需要专门写解析组件，也能看到底部参考来源
            // ==========================================
            StringBuilder appendText = new StringBuilder("\n\n---\n**参考资料：**\n");
            for (int i = 0; i < citationList.size(); i++) {
                CitationDTO dto = citationList.get(i);
                appendText.append(String.format("[%d] %s (第 %d 页)\n", 
                        i + 1, 
                        dto.getDocumentName(), 
                        dto.getPage()));
            }
            // 将拼装了参考文献的新文本覆盖回去
            String currentAnswer = context.getFinalAnswer() != null ? context.getFinalAnswer() : llmResponse;
            context.setFinalAnswer(currentAnswer + appendText.toString());
        } else {
            log.info("未从回答中提取到有效的文献引用。");
        }
    }

    @Override
    public String getName() {
        return "Citation Extraction Node";
    }
}