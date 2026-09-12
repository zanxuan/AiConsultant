package com.zx.consultant.orchestrator.service;

import com.zx.consultant.pending.model.PendingTask;

/**
 * 判断用户本轮输入是在回答 PendingTask 的澄清，还是提出了新问题。
 */
public interface ClarificationReplyClassifier {

    /**
     * @return true 表示应恢复原 Summary 任务；false 表示结束 PendingTask 并走正常编排。
     */
    boolean isAnsweringClarification(PendingTask pending, String currentInput);
}
