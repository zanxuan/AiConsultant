package com.zx.consultant.chat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zx.consultant.chat.dto.ConversationCreateReq;
import com.zx.consultant.chat.entity.Conversation;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.service.ConversationService;
import com.zx.consultant.chat.mapper.MessageMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageMapper messageMapper; // 用于查询聊天记录

    public ConversationController(ConversationService conversationService, MessageMapper messageMapper) {
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
    }

    // 1、新建会话
    @PostMapping
    public Conversation create(@RequestBody ConversationCreateReq req) {
        Conversation conversation = new Conversation();
        conversation.setKnowledgeId(req.getKnowledgeId());
        conversation.setTitle(req.getTitle());
        conversation.setCreateTime(LocalDateTime.now());
        conversationService.save(conversation);
        return conversation;
    }

    // 2、获取会话列表
    @GetMapping
    public List<Conversation> list() {
        // 实际业务中这里应该加上 userId 的过滤条件
        return conversationService.list();
    }

    // 3、获取会话详情
    @GetMapping("/{conversationId}")
    public Conversation detail(@PathVariable Long conversationId) {
        return conversationService.getById(conversationId);
    }

    // 4、删除会话
    @DeleteMapping("/{conversationId}")
    public boolean delete(@PathVariable Long conversationId) {
        return conversationService.removeById(conversationId);
    }

    // 6、获取聊天记录
    @GetMapping("/{conversationId}/messages")
    public List<Message> getMessages(@PathVariable Long conversationId) {
        // 利用 MP 的 Wrapper 条件构造器查询指定会话的历史记录，并按时间排序
        LambdaQueryWrapper<Message> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Message::getConversationId, conversationId)
                    .orderByAsc(Message::getCreateTime);
        return messageMapper.selectList(queryWrapper);
    }
}