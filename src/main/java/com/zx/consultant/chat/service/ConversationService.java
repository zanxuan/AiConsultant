package com.zx.consultant.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zx.consultant.chat.entity.Conversation;

public interface ConversationService extends IService<Conversation> {
    // MP 的 IService 已经包含了 save, getById, removeById, list 等标准方法
    // 如果有特殊业务，在这里额外声明
}