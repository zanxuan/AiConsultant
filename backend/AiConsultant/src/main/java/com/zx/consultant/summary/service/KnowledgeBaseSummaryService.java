package com.zx.consultant.summary.service;

import com.zx.consultant.workflow.context.WorkflowContext;

/**
 * 知识库总结：先分别总结各 READY 文档，再聚合生成知识库级最终总结。
 */
public interface KnowledgeBaseSummaryService {

    /**
     * 对当前知识库中已就绪文档分别摘要后，再聚合为知识库级总结。
     */
    WorkflowContext summarize(WorkflowContext context);
}
