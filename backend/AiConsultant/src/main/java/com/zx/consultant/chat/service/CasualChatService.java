package com.zx.consultant.chat.service;

import com.zx.consultant.workflow.context.WorkflowContext;


/**
 * 闲聊服务
 */
public interface CasualChatService {
    /**
     * 执行闲聊
     * @param context
     * @return
     */
    WorkflowContext execute(WorkflowContext context);
}
