package com.zx.consultant.orchestrator.service;

import com.zx.consultant.orchestrator.enums.SummaryTool;

/**
 * 意图已是 SUMMARY 时，选择单文档或整库总结工具。
 */
public interface SummaryPlanner {

    /**
     * 选择总结工具。模型输出无法解析时回退为单文档总结，避免误触发整库总结。
     */
    SummaryTool choose(String query);
}
