package com.zx.consultant.workflow.service;

import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.common.constant.MemoryConstant;
import com.zx.consultant.common.trace.TraceContext;
import com.zx.consultant.common.trace.TraceRecorder;
import com.zx.consultant.memory.service.MemoryService;
import com.zx.consultant.trace.service.TraceService;
import com.zx.consultant.workflow.context.WorkflowContext;
import com.zx.consultant.workflow.node.WorkflowNode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
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

    private final TraceService traceService;

    public WorkflowService(List<WorkflowNode> nodes,
                           MemoryService memoryService,
                           TraceService traceService) {
        this.nodes = nodes; 
        this.memoryService = memoryService;
        this.traceService = traceService;
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
        context.setTraceId(TraceContext.getTraceId());
        log.info("=== 开始执行 RAG 工作流，traceId={}, ConversationID: {} ===",
                context.getTraceId(), context.getConversationId());
        long startTime = System.currentTimeMillis();

        try {
            // 节点流转前：从 Redis 加载短期记忆（摘要 + 滑动窗口），供 Rewrite / Prompt 使用
            // Memory 当前不是独立 WorkflowNode，但仍纳入 Trace，便于定位记忆加载耗时
            TraceRecorder.record("Memory Load", () -> {
                if (context.getConversationId() != null) {
                    List<Message> historyMessages = memoryService.getRecentMessages(
                            context.getConversationId(),
                            MemoryConstant.MAX_HISTORY_MESSAGES);
                    context.setMemory(historyMessages);
                }
            });

            for (WorkflowNode node : nodes) {
                if (context.isEarlyStop()) {
                    log.info("工作流 earlyStop，跳过后续节点: {}", node.getName());
                    break;
                }
                TraceRecorder.record(node.getName(), () -> node.execute(context));
            }

            // 工作流结束后：把本轮「用户问题 + AI 回答」写入 Redis；超限时由 MemoryService 触发摘要压缩
            TraceRecorder.record("Memory Persist", () -> {
                if (context.getConversationId() != null
                        && context.getOriginalQuery() != null
                        && context.getFinalAnswer() != null
                        && !context.getFinalAnswer().isBlank()) {
                    memoryService.appendTurn(
                            context.getConversationId(),
                            context.getOriginalQuery(),
                            context.getFinalAnswer());
                }
            });
        } finally {
            // 无论成功失败，都将 Span 回写 Context、落库并打印摘要
            context.setNodeSpans(new ArrayList<>(TraceContext.getSpans()));
            persistTraceSpans(context);
            TraceRecorder.logSummary();
            log.info("=== RAG 工作流结束，traceId={}, 总耗时: {} ms ===",
                    context.getTraceId(), System.currentTimeMillis() - startTime);
        }

        return context;
    }

    /**
     * Trace 落库失败不影响主流程（Chat 结果仍可正常返回）
     */
    private void persistTraceSpans(WorkflowContext context) {
        try {
            traceService.saveSpans(context.getTraceId(), context.getNodeSpans());
        } catch (Exception e) {
            log.warn("Trace spans 落库失败, traceId={}, error={}",
                    context.getTraceId(), e.getMessage());
        }
    }
}
