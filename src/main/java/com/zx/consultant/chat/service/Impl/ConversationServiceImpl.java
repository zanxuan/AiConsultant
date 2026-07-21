package com.zx.consultant.chat.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.chat.entity.Conversation;
import com.zx.consultant.chat.mapper.ConversationMapper;
import com.zx.consultant.chat.service.ConversationService;
import org.springframework.stereotype.Service;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {
}