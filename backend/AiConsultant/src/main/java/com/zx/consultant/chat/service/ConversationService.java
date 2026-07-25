package com.zx.consultant.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zx.consultant.chat.dto.ConversationCreateReq;
import com.zx.consultant.chat.entity.Conversation;

import java.util.List;

public interface ConversationService extends IService<Conversation> {

    /**
     * 新建会话（绑定当前登录用户）
     */
    Conversation create(ConversationCreateReq req);

    /**
     * 查询当前用户的会话列表
     */
    List<Conversation> listByCurrentUser();

    /**
     * 查询会话详情
     */
    Conversation getDetail(Long conversationId);

    /**
     * 删除会话
     */
    boolean delete(Long conversationId);
}
