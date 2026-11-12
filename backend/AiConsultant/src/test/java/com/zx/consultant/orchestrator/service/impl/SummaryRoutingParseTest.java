package com.zx.consultant.orchestrator.service.impl;

import com.zx.consultant.orchestrator.enums.Intent;
import com.zx.consultant.orchestrator.enums.SummaryTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 只验证意图和总结工具的输出解析，不调用模型。
 */
class SummaryRoutingParseTest {

    private final IntentServiceImpl intentService = new IntentServiceImpl(null);
    private final SummaryPlannerImpl summaryPlanner = new SummaryPlannerImpl(null);

    @Test
    void intentKeepsThreeLabels() {
        assertEquals(Intent.RAG_QA, intentService.parse("RAG_QA"));
        assertEquals(Intent.RAG_QA, intentService.parse("rag"));
        assertEquals(Intent.CHAT, intentService.parse("CHAT"));
        assertEquals(Intent.SUMMARY, intentService.parse("SUMMARY"));
    }

    @Test
    void oldSummaryScopeStillCountsAsSummaryIntent() {
        assertEquals(Intent.SUMMARY, intentService.parse("SUMMARY DOCUMENT"));
        assertEquals(Intent.SUMMARY, intentService.parse("SUMMARY_KNOWLEDGE_BASE"));
        assertEquals(Intent.CHAT, intentService.parse(""));
        assertEquals(Intent.CHAT, intentService.parse("随便写的"));
    }

    @Test
    void summaryToolParsesDocumentAndKnowledgeBase() {
        assertEquals(SummaryTool.SUMMARIZE_DOCUMENT, summaryPlanner.parse("summarize_document"));
        assertEquals(SummaryTool.SUMMARIZE_KNOWLEDGE_BASE, summaryPlanner.parse("summarize_knowledge_base"));
        assertEquals(SummaryTool.SUMMARIZE_KNOWLEDGE_BASE, summaryPlanner.parse("SUMMARY KNOWLEDGE_BASE"));
        assertEquals(SummaryTool.SUMMARIZE_DOCUMENT, summaryPlanner.parse("SUMMARY DOCUMENT"));
    }

    @Test
    void unknownSummaryToolFallsBackToDocument() {
        assertEquals(SummaryTool.SUMMARIZE_DOCUMENT, summaryPlanner.parse(""));
        assertEquals(SummaryTool.SUMMARIZE_DOCUMENT, summaryPlanner.parse("不知道"));
        assertEquals(SummaryTool.SUMMARIZE_DOCUMENT,
                summaryPlanner.parse("summarize_document，不要总结 knowledge base"));
    }
}
