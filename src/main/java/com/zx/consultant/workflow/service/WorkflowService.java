package com.zx.consultant.workflow.service;

import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.common.constant.MemoryConstant;
import com.zx.consultant.memory.service.MemoryService;
import com.zx.consultant.workflow.context.WorkflowContext;
import com.zx.consultant.workflow.node.WorkflowNode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Workflow 编排中心，负责调度各个节点，不负责具体业务
 */
@Slf4j
@Service
public class WorkflowService {

    // 注入所有定义的节点，Spring 会自动根据 @Order 从小到大排序
    private final List<WorkflowNode> nodes;

    private final MemoryService memoryService;

    public WorkflowService(List<WorkflowNode> nodes, MemoryService memoryService) {
        this.nodes = nodes; 
        this.memoryService = memoryService;
        // 【增加可观测性】在项目启动时，打印出装载的节点执行链，一目了然
        String nodeChain = nodes.stream()
                .map(WorkflowNode::getName)
                .collect(Collectors.joining(" -> "));
        log.info("【工作流引擎初始化完成】执行链路: {}", nodeChain);
    }

    /**
     * 执行整个问答工作流
     */
    public WorkflowContext run(WorkflowContext context) {
        log.info("=== 开始执行 RAG 工作流，ConversationID: {} ===", context.getConversationId());
        long startTime = System.currentTimeMillis();

        // 【新增逻辑】：在节点流转之前，提前加载历史对话并放入上下文中
        if (context.getConversationId() != null) {
            List<Message> historyMessages = memoryService.getRecentMessages(context.getConversationId(),MemoryConstant.MAX_HISTORY_MESSAGES);
            // 将历史消息装载进上下文，供后续的 RewriteNode 和 PromptNode 使用
            context.setMemory(historyMessages); 
        }

        for (WorkflowNode node : nodes) {
            // 后续可在此处添加 Evaluate Node 判断是否需要阻断或跳过
            // 例如：if (context.isEarlyStop()) break;
            
            long nodeStartTime = System.currentTimeMillis();
            node.execute(context);
            long cost = System.currentTimeMillis() - nodeStartTime;
            
            log.debug("节点 [{}] 执行耗时: {} ms", node.getName(), cost);
        }

        log.info("=== RAG 工作流执行完毕，总耗时: {} ms ===", System.currentTimeMillis() - startTime);
        return context;
    }
}