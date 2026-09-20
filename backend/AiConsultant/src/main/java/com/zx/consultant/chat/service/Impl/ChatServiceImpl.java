package com.zx.consultant.chat.service.Impl;
import com.zx.consultant.chat.dto.ChatReq;
import com.zx.consultant.chat.dto.ChatTaskResp;
import com.zx.consultant.chat.entity.Conversation;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.mapper.MessageMapper;
import com.zx.consultant.chat.service.ChatService;
import com.zx.consultant.chat.service.ConversationService;
import com.zx.consultant.chat.sse.SseEmitterManager;
import com.zx.consultant.chat.task.ChatTask;
import com.zx.consultant.chat.task.ChatTaskRegistry;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.common.trace.TraceContext;
import com.zx.consultant.common.utils.BaseContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final ChatTaskRegistry chatTaskRegistry;
    private final ChatWorkflowTask chatWorkflowTask;
    private final SseEmitterManager sseEmitterManager;

    public ChatServiceImpl(MessageMapper messageMapper,
                           ConversationService conversationService,
                           ChatTaskRegistry chatTaskRegistry,
                           ChatWorkflowTask chatWorkflowTask,
                           SseEmitterManager sseEmitterManager) {
        this.messageMapper = messageMapper;
        this.conversationService = conversationService;
        this.chatTaskRegistry = chatTaskRegistry;
        this.chatWorkflowTask = chatWorkflowTask;
        this.sseEmitterManager = sseEmitterManager;
    }

    /**
     * 用户提问：校验会话、用户消息落库、创建任务并启动后台 Workflow，立即返回 taskId。
     */
    @Override
    public ChatTaskResp ask(ChatReq req) {
        log.info("用户提问：{}, traceId={}", req.getMessage(), TraceContext.getTraceId());

        Conversation conversation = conversationService.getById(req.getConversationId());
        if (conversation == null) {
            throw new BaseException("会话不存在");
        }

        // 1. 落库用户提问（仍在 HTTP 线程，事务边界与改造前一致：无外层 @Transactional）
        Message userMessage = new Message();
        userMessage.setConversationId(req.getConversationId());
        userMessage.setRole("user");
        userMessage.setContent(req.getMessage());
        messageMapper.insert(userMessage);

        // 2. 创建 Task，并把当前请求的 userId / traceId 交给后台线程
        ChatTask task = chatTaskRegistry.create(req.getConversationId());
        Long userId = BaseContext.getCurrentId();
        String traceId = TraceContext.getTraceId();
        // 在 HTTP ask 线程绑定，供 SSE 超时/断开时落库；不能依赖 stream() 或 @Async 的 ThreadLocal
        sseEmitterManager.bindTraceId(task.getTaskId(), traceId);
        log.info("创建 Chat 任务并启动后台执行, taskId={}, knowledgeId={}",
                task.getTaskId(), conversation.getKnowledgeId());
        chatWorkflowTask.run(task.getTaskId(), req, conversation.getKnowledgeId(), userId, traceId);

        ChatTaskResp resp = new ChatTaskResp();
        resp.setTaskId(task.getTaskId());
        return resp;
    }

    /**
     * 按 taskId 建立 SSE。若后台已完成，立即补推 complete + ChatResp。
     */
    @Override
    public SseEmitter stream(String taskId) {
        ChatTask task = chatTaskRegistry.get(taskId);
        if (task == null) {
            throw new BaseException("任务不存在");
        }

        // 加锁：防止并发问题（多个浏览器同时连同一个taskId的SSE）
        synchronized (task) {
            // 注册SseEmitter，保存到管理器，后续后台线程通过这个emitter推消息
            SseEmitter emitter = sseEmitterManager.register(taskId);
            // 如果后台已完成 / 已失败，立即补推，避免前端空等
            if (task.getResult() != null) {
                sseEmitterManager.sendComplete(taskId, task.getResult());
                chatTaskRegistry.remove(taskId);
            } else if (task.getErrorMessage() != null) {
                sseEmitterManager.sendError(taskId, task.getErrorMessage());
                chatTaskRegistry.remove(taskId);
            }
            return emitter;
        }
    }
}
