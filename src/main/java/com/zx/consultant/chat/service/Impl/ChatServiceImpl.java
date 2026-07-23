package com.zx.consultant.chat.service.Impl;

import com.zx.consultant.chat.dto.ChatReq;
import com.zx.consultant.chat.dto.ChatResp;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.mapper.MessageMapper;
import com.zx.consultant.chat.service.ChatService;
import com.zx.consultant.workflow.context.WorkflowContext;
import com.zx.consultant.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;


@Service
public class ChatServiceImpl implements ChatService {

    private final WorkflowService workflowService;
    private final MessageMapper messageMapper;

    public ChatServiceImpl(WorkflowService workflowService, MessageMapper messageMapper) {
        this.workflowService = workflowService;
        this.messageMapper = messageMapper;
    }

    public ChatResp ask(ChatReq req) {
        // 1. 落库用户提问
        Message userMessage = new Message();
        userMessage.setConversationId(req.getConversationId());
        userMessage.setRole("user");
        userMessage.setContent(req.getMessage());
        messageMapper.insert(userMessage);

        // 2. 触发 Workflow 核心引擎
        WorkflowContext context = new WorkflowContext();
        context.setConversationId(req.getConversationId());
        context.setOriginalQuery(req.getMessage());
        // TODO :异常处理,V2 的 fallback
        context = workflowService.run(context);

        // 3. 落库 AI 回答
        Message aiMessage = new Message();
        aiMessage.setConversationId(req.getConversationId());
        aiMessage.setRole("assistant");
        aiMessage.setContent(context.getFinalAnswer());
        aiMessage.setReference(context.getCitations());
        messageMapper.insert(aiMessage);

        // 4. 组装并返回标准格式
        ChatResp resp = new ChatResp();
        resp.setAnswer(context.getFinalAnswer());
        // 简单处理引用来源：假设 Citation 节点存入的是逗号分隔的字符串
        if (context.getCitations() != null && !context.getCitations().isBlank()) {
            resp.setSources(Arrays.asList(context.getCitations().split(",")));
        } else {
            resp.setSources(List.of());
        }
        return resp;
    }
}