package com.zx.consultant.chat.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.chat.dto.ChatReq;
import com.zx.consultant.chat.dto.ChatResp;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.mapper.MessageMapper;
import com.zx.consultant.chat.sse.SseEmitterManager;
import com.zx.consultant.chat.task.ChatTask;
import com.zx.consultant.chat.task.ChatTaskRegistry;
import com.zx.consultant.common.trace.TraceContext;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.orchestrator.Orchestrator;
import com.zx.consultant.rag.dto.CitationDTO;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

/**
 * 后台执行原 ChatServiceImpl.ask() 中的 Orchestrator / 落库 / ChatResp 逻辑。
 * 单独 Bean 是为了让 @Async 生效（同类自调用不会走代理）。
 */
@Slf4j
@Service
public class ChatWorkflowTask {

    private final Orchestrator orchestrator;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final ChatTaskRegistry chatTaskRegistry;
    private final SseEmitterManager sseEmitterManager;

    public ChatWorkflowTask(Orchestrator orchestrator,
                            MessageMapper messageMapper,
                            ObjectMapper objectMapper,
                            ChatTaskRegistry chatTaskRegistry,
                            SseEmitterManager sseEmitterManager) {
        this.orchestrator = orchestrator;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
        this.chatTaskRegistry = chatTaskRegistry;
        this.sseEmitterManager = sseEmitterManager;
    }

    /**
     * HTTP 线程必须先把 userId / traceId 传进来：
     * @Async 跑在别的线程，且请求返回后 TraceIdFilter 会清掉原 ThreadLocal。
     * Orchestrator → PendingTaskService 依赖 BaseContext.userId，不能丢。
     */
    @Async
    public void run(String taskId, ChatReq req, Long knowledgeId, Long userId, String traceId) {
        if (userId != null) {
            BaseContext.setCurrentId(userId);
        }
        TraceContext.init(traceId);
        TraceContext.setTaskId(taskId);
        TraceContext.setProgressSink(message -> sseEmitterManager.sendProgress(taskId, message));

        try {
            log.info("后台任务开始, taskId={}, knowledgeId={}, traceId={}",
                    taskId, knowledgeId, TraceContext.getTraceId());
            WorkflowContext context = new WorkflowContext();
            context.setConversationId(req.getConversationId());
            context.setKnowledgeId(knowledgeId);
            context.setOriginalQuery(req.getMessage());

            String answer;
            List<CitationDTO> references;
            try {
                // 与改造前 ask() 相同：走 Orchestrator，RAG 时内部才会 WorkflowService.run()
                context = orchestrator.dispatch(context);
                answer = context.getFinalAnswer();
                references = context.getCitations() != null
                        ? context.getCitations()
                        : Collections.emptyList();
            } catch (Exception e) {
                log.error("Workflow 执行失败, conversationId={}, traceId={}",
                        req.getConversationId(), TraceContext.getTraceId(), e);
                answer = "当前智能问答服务暂时不可用，请稍后重试";
                references = Collections.emptyList();
            }

            log.info("落库 AI 回答");
            Message aiMessage = new Message();
            aiMessage.setConversationId(req.getConversationId());
            aiMessage.setRole("assistant");
            aiMessage.setContent(answer);
            aiMessage.setReference(serializeReferences(references));
            messageMapper.insert(aiMessage);

            ChatResp resp = new ChatResp();
            resp.setAnswer(answer);
            resp.setReferences(references);
            completeAndPush(taskId, resp);
        } finally {
            TraceContext.clear();
            BaseContext.removeCurrentId();
        }
    }

    /**
     * 完成任务并推送结果：设置任务结果缓冲，调用 SseEmitterManager 推送 complete 事件。
     */
    private void completeAndPush(String taskId, ChatResp resp) {
        ChatTask task = chatTaskRegistry.get(taskId);
        if (task == null) {
            log.warn("任务不存在，跳过 SSE 推送, taskId={}", taskId);
            return;
        }
        // 与 stream() 共用一把锁，避免「结果已完成但 emitter 尚未登记」时漏推
        synchronized (task) {
            task.setResult(resp);
            sseEmitterManager.sendComplete(taskId, resp);
        }
    }

    /**
     * 序列化引用列表：用于落库。
     */
    private String serializeReferences(List<CitationDTO> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            log.error("引用列表序列化失败", e);
            return null;
        }
    }
}
