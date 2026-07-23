package com.zx.consultant.chat.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.mapper.MessageMapper;
import com.zx.consultant.chat.service.MessageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    /**
     * 查询会话消息
     * @param conversationId
     * @return
     */
    @Override
    public List<Message> listByConversationId(Long conversationId) {
        return list(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getCreateTime));
    }
}
