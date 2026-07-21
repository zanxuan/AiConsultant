package com.zx.consultant.workflow.service;

import com.zx.consultant.workflow.context.WorkflowContext;
import com.zx.consultant.workflow.node.WorkflowNode;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Workflow 编排中心，负责调度各个节点，不负责具体业务
 */
@Service
public class WorkflowService {

    // 注入所有定义的节点，按顺序编排
    private final List<WorkflowNode> nodes;

    public WorkflowService(List<WorkflowNode> nodes) {
        // 实际开发中可以通过 @Order 注解或代码手动编排节点顺序
        this.nodes = nodes; 
    }

    /**
     * 执行整个问答工作流
     */
    public WorkflowContext run(WorkflowContext context) {
        for (WorkflowNode node : nodes) {
            // 后续可在此处添加 Evaluate Node 判断是否需要阻断或跳过
            node.execute(context);
        }
        return context;
    }
}