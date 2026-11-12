package com.zx.consultant.orchestrator.enums;

/**
 * SUMMARY 入口上的一次工具选择。
 * 不是主意图，也不进入多轮 tool 循环。
 */
public enum SummaryTool {
    /** 总结某一篇文档；目标不明时由单文档总结发起澄清 */
    SUMMARIZE_DOCUMENT,
    /** 用户明确要求概括整个知识库 */
    SUMMARIZE_KNOWLEDGE_BASE
}
