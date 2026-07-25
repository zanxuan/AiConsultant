package com.zx.consultant.chat.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.chat.dto.ConversationCreateReq;
import com.zx.consultant.chat.entity.Conversation;
import com.zx.consultant.chat.mapper.ConversationMapper;
import com.zx.consultant.chat.service.ConversationService;
import com.zx.consultant.common.utils.BaseContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    /**
     * 创建会话
     * @param req
     * @return
     */
    @Override
    public Conversation create(ConversationCreateReq req) {
        Conversation conversation = new Conversation();
        conversation.setKnowledgeId(req.getKnowledgeId());
        conversation.setTitle(req.getTitle());
        conversation.setUserId(BaseContext.getCurrentId());
        save(conversation);
        return conversation;
    }

    /**
     * 查询当前用户的会话列表
     * @return
     */
    @Override
    public List<Conversation> listByCurrentUser() {
        Long userId = BaseContext.getCurrentId();
        return list(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getUpdateTime));
    }

    /**
     * 查询会话详情
     * @param conversationId
     * @return
     */
    @Override
    public Conversation getDetail(Long conversationId) {
        return getById(conversationId);
    }

    /**
     * 删除会话
     * @param conversationId
     * @return
     */
    @Override
    public boolean delete(Long conversationId) {
        return removeById(conversationId);
    }
}
