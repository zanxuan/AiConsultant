package com.zx.consultant.workflow.node;

import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.workflow.context.WorkflowContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 大模型生成节点
 * @author zx
 * @date 2026-07-20
 * LLMNode
 */
@Component
@Order(4) // 定义节点顺序，通常在检索节点(3)之后执行
public class LLMNode implements WorkflowNode {

    private final LLMService llmService;

    // TODO: 后期可以注入 PromptService 来专门负责模板组装
    // private final PromptService promptService;

    public LLMNode(LLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    public void execute(WorkflowContext context) {
        // 1. 获取最终要提问的 Query
        String query = context.getRewrittenQuery() != null 
                ? context.getRewrittenQuery() 
                : context.getOriginalQuery();

        // 2. 获取检索节点(RetrieveNode)查出来的相关文档
        List<RetrievedChunk> docs = context.getRetrievedDocuments();

        // 3. 构建 Prompt (此处做简单拼接，实际可替换为调用 promptService)
        String prompt = buildPrompt(query, docs);

        // 4. 调用 LLM 获取答案
        // 注：这里以非流式回答为例。如果是流式请求，可以调用 streamGenerateAnswer 并将 Flux 存入 context
        String answer = llmService.generateAnswer(prompt);

        // 5. 将生成的答案回写到上下文，供后续节点或最终结果封装使用
        context.setFinalAnswer(answer); 
    }

    @Override
    public String getName() {
        return "LLM Generation Node";
    }

    /**
     * 简单的 Prompt 组装逻辑（后期建议抽离到专门的 PromptService 中）
     */
    private String buildPrompt(String query, List<RetrievedChunk> docs) {
        if (docs == null || docs.isEmpty()) {
            return query;
        }

        // 将检索到的 chunk 内容拼接成上下文参考资料
        String contextInfo = docs.stream()
                .map(RetrievedChunk::getContent) // 假设存在 getContent() 方法获取文本
                .collect(Collectors.joining("\n\n"));

        return String.format(
                "请根据以下参考资料回答我的问题。\n\n【参考资料】：\n%s\n\n【我的问题】：\n%s",
                contextInfo, 
                query
        );
    }
}