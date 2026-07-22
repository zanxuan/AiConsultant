package com.zx.consultant.workflow.node;

import com.zx.consultant.rag.service.RewriteService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 重写节点
 * 负责根据历史会话重写用户当前问题，以利于后续的向量检索
 * @author zx
 * @date 2026-07-20
 */
@Slf4j
@Component
@Order(2) // 建议设为 2。如果是工作流，建议 Order(1) 为 MemoryNode，负责查询并将历史记录放入 Context
public class RewriteNode implements WorkflowNode {

    private final RewriteService rewriteService;

    public RewriteNode(RewriteService rewriteService) {
        this.rewriteService = rewriteService;
    }

    @Override
    public void execute(WorkflowContext context) {
        log.info(">>> 执行节点: {} >>>", getName());
        
        // 1. 获取上下文中的原问题和历史记忆
        String originalQuery = context.getOriginalQuery();
        // 确保上一个节点（或工作流起点）已经获取并赋值了 memory
        var memory = context.getMemory(); 

        // 2. 调用 RewriteService (注意方法名是 rewriteQuery)
        String rewritten = rewriteService.rewriteQuery(originalQuery, memory);
        
        log.info("原问题: [{}], 改写后: [{}]", originalQuery, rewritten);

        // 3. 将改写后的问题存回上下文，供后续的 RetrieverNode 使用
        context.setRewrittenQuery(rewritten);
    }

    @Override
    public String getName() {
        return "Query Rewrite Node";
    }
}