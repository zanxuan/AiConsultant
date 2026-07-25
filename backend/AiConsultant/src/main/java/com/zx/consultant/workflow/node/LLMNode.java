package com.zx.consultant.workflow.node;

import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 大模型生成节点
 * 负责调用底层 LLM 服务获取回答
 * @author zx
 * @date 2026-07-20
 */
@Slf4j
@Component
@Order(5) // 修改顺序：排在 PromptNode (Order 4) 之后执行
public class LLMNode implements WorkflowNode {

    private final LLMService llmService;

    // 只需要注入 LLMService 即可，Prompt 的组装已经前置到了 PromptNode
    public LLMNode(LLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    public void execute(WorkflowContext context) {
        log.info(">>> 执行节点: {} >>>", getName());

        // 1. 从上下文中获取已经在 PromptNode 构建好的结构化 PromptRequest
        PromptRequest promptRequest = context.getPrompt();
        if (promptRequest == null) {
            throw new IllegalStateException("PromptRequest 不能为空，请检查 PromptNode 是否正确执行");
        }

        log.info("正在调用 LLMService 生成回答...");

        // 2. 调用 LLM 获取答案 (直接传入结构化对象)
        // 注：这里以非流式回答为例。流式请求可在此处调用 streamGenerateAnswer，并由 Controller 处理 SSE 响应
        String answer = llmService.generateAnswer(promptRequest);

        // 3. 将生成的答案回写到上下文
        context.setLlmResponse(answer); // 保存原始回答（可能含 cite 标记），供 CitationNode 提取引用
        context.setFinalAnswer(answer); // 先写入，CitationNode 会清洗标记并与 references 分离

        log.info("LLM 节点执行完毕，已生成回答。");
    }

    @Override
    public String getName() {
        return "LLM Generation Node";
    }
}