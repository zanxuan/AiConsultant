package com.zx.consultant.chat.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.chat.dto.ConversationCreateReq;
import com.zx.consultant.chat.entity.Conversation;
import com.zx.consultant.chat.mapper.ConversationMapper;
import com.zx.consultant.chat.service.ConversationService;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.knowledge.entity.KnowledgeBase;
import com.zx.consultant.knowledge.mapper.KnowledgeBaseMapper;
import com.zx.consultant.pending.service.PendingTaskService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    private final PendingTaskService pendingTaskService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public ConversationServiceImpl(PendingTaskService pendingTaskService,
                                   KnowledgeBaseMapper knowledgeBaseMapper) {
        this.pendingTaskService = pendingTaskService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

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
        fillKnowledgeNames(Collections.singletonList(conversation));
        return conversation;
    }

    /**
     * 查询当前用户的会话列表
     * @return
     */
    @Override
    public List<Conversation> listByCurrentUser() {
        Long userId = BaseContext.getCurrentId();
        List<Conversation> conversations = list(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getUpdateTime));
        // 表里只有 knowledgeId，历史列表展示的是知识库名称
        fillKnowledgeNames(conversations);
        return conversations;
    }

    /**
     * 查询会话详情
     * @param conversationId
     * @return
     */
    @Override
    public Conversation getDetail(Long conversationId) {
        Conversation conversation = getById(conversationId);
        if (conversation != null) {
            fillKnowledgeNames(Collections.singletonList(conversation));
        }
        return conversation;
    }

    /**
     * 按已有的 knowledgeId 回填名称，复用知识库 Mapper，不改会话表。
     */
    private void fillKnowledgeNames(List<Conversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }
        Set<Long> knowledgeIds = conversations.stream()
                .map(Conversation::getKnowledgeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (knowledgeIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = knowledgeBaseMapper.selectBatchIds(knowledgeIds).stream()
                .filter(base -> base.getId() != null && base.getName() != null)
                .collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName, (left, right) -> left));
        for (Conversation conversation : conversations) {
            Long knowledgeId = conversation.getKnowledgeId();
            if (knowledgeId == null) {
                continue;
            }
            conversation.setKnowledgeName(nameById.get(knowledgeId));
        }
    }

    /**
     * 删除会话
     * @param conversationId
     * @return
     */
    @Override
    public boolean delete(Long conversationId) {
        //删除任务
        pendingTaskService.delete(conversationId);
        return removeById(conversationId);
    }
}
