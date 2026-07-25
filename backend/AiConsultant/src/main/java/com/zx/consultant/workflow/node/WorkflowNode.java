package com.zx.consultant.workflow.node;

import com.zx.consultant.workflow.context.WorkflowContext;

/**
 * 工作流节点抽象接口，每个节点均可独立扩展
 */
public interface WorkflowNode {
    /**
     * 执行当前节点逻辑
     * @param context 工作流上下文
     */
    void execute(WorkflowContext context);
    
    /**
     * 节点名称
     */
    String getName();
}