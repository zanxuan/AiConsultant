package com.zx.consultant.workflow.node;

import com.zx.consultant.rag.service.RewriteService;
import com.zx.consultant.workflow.context.WorkflowContext;
import com.zx.consultant.workflow.node.WorkflowNode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 重写节点
 * @author zx
 * @date 2026-07-20
 * RewriteNode
 */
//@Component
//@Order(1) // 定义节点顺序
public class RewriteNode implements WorkflowNode {

    private final RewriteService rewriteService;

    public RewriteNode(RewriteService rewriteService) {
        this.rewriteService = rewriteService;
    }

    @Override
    public void execute(WorkflowContext context) {
        //String rewritten = rewriteService.rewrite(context.getOriginalQuery(), context.getMemory());
        //context.setRewrittenQuery(rewritten);
    }

    @Override
    public String getName() {
        return "Query Rewrite Node";
    }
}