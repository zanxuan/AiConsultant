package com.zx.consultant.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.consultant.rag.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
    
}
