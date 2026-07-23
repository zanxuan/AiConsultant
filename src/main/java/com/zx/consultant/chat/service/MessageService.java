package com.zx.consultant.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zx.consultant.chat.entity.Message;

import java.util.List;

public interface MessageService extends IService<Message> {

    /**
     * 按会话 ID 查询聊天记录（按创建时间升序）
     */
    List<Message> listByConversationId(Long conversationId);
}
