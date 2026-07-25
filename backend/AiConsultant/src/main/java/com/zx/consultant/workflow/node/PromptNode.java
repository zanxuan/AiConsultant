package com.zx.consultant.workflow.node;

import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.rag.service.PromptService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 提示词组装节点
 */
@Slf4j
@Component
@Order(4)
public class PromptNode implements WorkflowNode {

    private final PromptService promptService;

    // 直接注入我们已经完善好的 PromptService
    public PromptNode(PromptService promptService) {
        this.promptService = promptService;
    }

    @Override
    public void execute(WorkflowContext context) {
        log.info(">>> 执行节点: {} >>>", getName());

        // 1. 从上下文提取基础数据
        String originalQuery = context.getOriginalQuery();
        var memory = context.getMemory();
        var docs = context.getRetrievedDocuments();

        // 2. 核心业务逻辑交给 Service 处理
        // （此时 PromptService.buildPrompt 方法返回的应该是 PromptRequest 对象）
        PromptRequest promptRequest = promptService.buildPrompt(originalQuery, memory, docs);

        // 3. 将结果放回上下文，供 LLMNode 使用
        context.setPrompt(promptRequest);
        
        log.info("Prompt 组装完成并已存入 Context。");
    }

    @Override
    public String getName() {
        return "Prompt Builder Node";
    }
}