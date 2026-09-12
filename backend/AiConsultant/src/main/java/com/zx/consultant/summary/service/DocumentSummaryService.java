package com.zx.consultant.summary.service;

import com.zx.consultant.workflow.context.WorkflowContext;

/**
 * 单文档总结：先硬匹配文件名，未完全命中再由问答 LLM 按候选名称匹配，不猜测用户要哪一篇。
 */
public interface DocumentSummaryService {

    /**
     * 定位唯一目标文档并生成总结。目标不明确时只标记追问，不猜测文档。
     */
    WorkflowContext summarize(WorkflowContext context);
}
