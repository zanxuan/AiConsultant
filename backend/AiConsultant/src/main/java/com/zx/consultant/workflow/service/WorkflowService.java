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

        // 节点流转前：从 Redis 加载短期记忆（摘要 + 滑动窗口），供 Rewrite / Prompt 使用
        if (context.getConversationId() != null) {
            List<Message> historyMessages = memoryService.getRecentMessages(
                    context.getConversationId(),
                    MemoryConstant.MAX_HISTORY_MESSAGES);
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

        // 工作流结束后：把本轮「用户问题 + AI 回答」写入 Redis；超限时由 MemoryService 触发摘要压缩
        if (context.getConversationId() != null
                && context.getOriginalQuery() != null
                && context.getFinalAnswer() != null
                && !context.getFinalAnswer().isBlank()) {
            memoryService.appendTurn(
                    context.getConversationId(),
                    context.getOriginalQuery(),
                    context.getFinalAnswer());
        }

        log.info("=== RAG 工作流执行完毕，总耗时: {} ms ===", System.currentTimeMillis() - startTime);
        return context;
    }
}
