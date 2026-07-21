package com.zx.consultant.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.consultant.chat.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
    
}
