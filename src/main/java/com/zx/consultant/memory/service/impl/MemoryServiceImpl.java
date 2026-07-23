package com.zx.consultant.memory.service.impl;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.chat.mapper.MessageMapper;
import com.zx.consultant.memory.service.MemoryService;
import com.zx.consultant.common.constant.MemoryConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final MessageMapper messageMapper;

    


    /**
     * 获取最近的N条消息
     */
    @Override
    public List<Message> getRecentMessages(Long conversationId, int limit) {
        log.info(
            "加载历史消息 conversationId={}, limit={}",
            conversationId,
            limit
         );
        // 从数据库按时间倒序查出最近的 N 条
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, conversationId)
               .orderByDesc(Message::getCreateTime)
               .last("LIMIT " + limit);
               
        List<Message> recentMessages = messageMapper.selectList(wrapper);
        
        // 因为是倒序查出来的（最新的一条在最前面），给大模型喂数据时需要正序（符合时间发展）
        Collections.reverse(recentMessages);
        
        return recentMessages;
    }
    
    // 提供一个默认 limit 的重载方法供外部直接调用
    public List<Message> getRecentMessages(Long conversationId) {
        return getRecentMessages(conversationId, MemoryConstant.MAX_HISTORY_MESSAGES);
    }
}