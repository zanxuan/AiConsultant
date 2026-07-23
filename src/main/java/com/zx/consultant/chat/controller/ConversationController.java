package com.zx.consultant.chat.controller;

import com.zx.consultant.chat.dto.ConversationCreateReq;
import com.zx.consultant.chat.entity.Conversation;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.service.ConversationService;
import com.zx.consultant.chat.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * 创建会话
     * @param req
     * @return
     */
    @PostMapping
    public Conversation create(@RequestBody ConversationCreateReq req) {
        return conversationService.create(req);
    }

    /**
     * 查询当前用户的会话列表
     * @return
     */
    @GetMapping
    public List<Conversation> list() {
        return conversationService.listByCurrentUser();
    }

    /**
     * 查询会话详情
     * @param conversationId
     * @return
     */
    @GetMapping("/{conversationId}")
    public Conversation detail(@PathVariable Long conversationId) {
        return conversationService.getDetail(conversationId);
    }

    /**
     * 删除会话
     * @param conversationId
     * @return
     */
    @DeleteMapping("/{conversationId}")
    public boolean delete(@PathVariable Long conversationId) {
        return conversationService.delete(conversationId);
    }

    /**
     * 查询会话消息
     * @param conversationId
     * @return
     */
    @GetMapping("/{conversationId}/messages")
    public List<Message> getMessages(@PathVariable Long conversationId) {
        return messageService.listByConversationId(conversationId);
    }
}
