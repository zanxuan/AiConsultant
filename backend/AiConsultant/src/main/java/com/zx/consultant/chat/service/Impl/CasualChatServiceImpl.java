package com.zx.consultant.chat.service.Impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import com.zx.consultant.chat.service.CasualChatService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.memory.service.MemoryService;
import com.zx.consultant.common.trace.TraceRecorder;
import com.zx.consultant.trace.service.TraceService;
import com.zx.consultant.common.trace.TraceContext;
import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.rag.service.PromptService;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.common.constant.MemoryConstant;


/**
 * 闲聊服务实现类
 */
@Slf4j
@Service        
public class CasualChatServiceImpl implements CasualChatService{
    private final LLMService llmService;
    private final MemoryService memoryService;
    private final TraceService traceService;
    private final PromptService promptService;
    public CasualChatServiceImpl(LLMService llmService, MemoryService memoryService,TraceService traceService, PromptService promptService) {
        this.llmService = llmService;
        this.memoryService = memoryService;
        this.traceService = traceService;
        this.promptService = promptService;
    }



    /**
     * 执行闲聊
     * @param context 上下文
     * @return 上下文
     */
    @Override
    public WorkflowContext execute(WorkflowContext context) {
        context.setTraceId(TraceContext.getTraceId());
        long startTime = System.currentTimeMillis();
        log.info("=== 开始执行闲聊，traceId={}, ConversationID: {} ===",
                context.getTraceId(), context.getConversationId());

        try {
            // 执行开始时只读一次短期记忆，放入 context.memory
            if (context.getConversationId() != null) {
                List<Message> historyMessages = memoryService.getRecentMessages(
                        context.getConversationId(),
                        MemoryConstant.MAX_HISTORY_MESSAGES);
                context.setMemory(historyMessages);
            }

            // CHAT 只使用当前问题和短期记忆，不注入检索文档
            String originalQuery = context.getOriginalQuery();
            var memory = context.getMemory();
            PromptRequest promptRequest = promptService.buildChatPrompt(originalQuery, memory);
            context.setPrompt(promptRequest);

            // 走已有 LLM 降级逻辑，不直接创建底层模型
            String answer = llmService.generateAnswer(promptRequest);
            context.setLlmResponse(answer);
            context.setFinalAnswer(answer);

            // LLM 成功后再写入本轮 user/assistant
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
            log.info("=== 闲聊结束，traceId={}, 总耗时: {} ms ===",
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
