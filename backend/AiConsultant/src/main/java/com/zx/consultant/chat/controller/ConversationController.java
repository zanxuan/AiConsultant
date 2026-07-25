package com.zx.consultant.chat.controller;

import com.zx.consultant.chat.dto.ConversationCreateReq;
import com.zx.consultant.chat.entity.Conversation;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.service.ConversationService;
import com.zx.consultant.chat.service.MessageService;
import com.zx.consultant.common.result.Result;

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
    public Result<Conversation> create(@RequestBody ConversationCreateReq req) {
        return Result.success(conversationService.create(req));
    }

    /**
     * 查询当前用户的会话列表
     * @return
     */
    @GetMapping
    public Result<List<Conversation>> list() {
        return Result.success(conversationService.listByCurrentUser());
    }

    /**
     * 查询会话详情
     * @param conversationId
     * @return
     */
    @GetMapping("/{conversationId}")
    public Result<Conversation> detail(@PathVariable Long conversationId) {
        return Result.success(conversationService.getDetail(conversationId));
    }

    /**
     * 删除会话
     * @param conversationId
     * @return
     */
    @DeleteMapping("/{conversationId}")
    public Result<Boolean> delete(@PathVariable Long conversationId) {
        return Result.success(conversationService.delete(conversationId));
    }

    /**
     * 查询会话消息
     * @param conversationId
     * @return
     */
    @GetMapping("/{conversationId}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long conversationId) {
        return Result.success(messageService.listByConversationId(conversationId));
    }
}
