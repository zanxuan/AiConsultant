package com.zx.consultant.chat.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.chat.dto.ChatReq;
import com.zx.consultant.chat.dto.ChatResp;
import com.zx.consultant.chat.entity.Conversation;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.mapper.MessageMapper;
import com.zx.consultant.chat.service.ChatService;
import com.zx.consultant.chat.service.ConversationService;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.rag.dto.CitationDTO;
import com.zx.consultant.workflow.context.WorkflowContext;
import com.zx.consultant.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j

@Service
public class ChatServiceImpl implements ChatService {

    private final WorkflowService workflowService;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl(WorkflowService workflowService,
                           MessageMapper messageMapper,
                           ConversationService conversationService,
                           ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.messageMapper = messageMapper;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    public ChatResp ask(ChatReq req) {
        log.info("用户提问：{}", req.getMessage());

        Conversation conversation = conversationService.getById(req.getConversationId());
        if (conversation == null) {
            throw new BaseException("会话不存在");
        }
        if (conversation.getKnowledgeId() == null) {
            throw new BaseException("会话未绑定知识库，无法检索");
        }

        // 1. 落库用户提问
        Message userMessage = new Message();
        userMessage.setConversationId(req.getConversationId());
        userMessage.setRole("user");
        userMessage.setContent(req.getMessage());
        messageMapper.insert(userMessage);

        // 2. 触发 Workflow 核心引擎（带上知识库隔离 ID）
        log.info("触发 Workflow 核心引擎, knowledgeId={}", conversation.getKnowledgeId());
        WorkflowContext context = new WorkflowContext();
        context.setConversationId(req.getConversationId());
        context.setKnowledgeId(conversation.getKnowledgeId());
        context.setOriginalQuery(req.getMessage());
        // TODO(V2): 增加异常处理, fallback 机制
        context = workflowService.run(context);

        List<CitationDTO> references = context.getCitations() != null
                ? context.getCitations()
                : Collections.emptyList();

        // 3. 落库 AI 回答（content 存纯回答，reference 存结构化引用 JSON）
        log.info("落库 AI 回答");
        Message aiMessage = new Message();
        aiMessage.setConversationId(req.getConversationId());
        aiMessage.setRole("assistant");
        aiMessage.setContent(context.getFinalAnswer());
        aiMessage.setReference(serializeReferences(references));
        messageMapper.insert(aiMessage);

        // 4. 组装并返回：answer 与 references 同级分离
        log.info("组装并返回标准格式");
        ChatResp resp = new ChatResp();
        resp.setAnswer(context.getFinalAnswer());
        resp.setReferences(references);
        return resp;
    }

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
