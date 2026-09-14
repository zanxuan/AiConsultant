package com.zx.consultant.summary.service.impl;
import com.zx.consultant.common.trace.TraceContext;
import com.zx.consultant.common.trace.TraceRecorder;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.memory.service.MemoryService;
import com.zx.consultant.summary.service.KnowledgeBaseSummaryService;
import com.zx.consultant.summary.service.SummaryDocumentLoader;
import com.zx.consultant.summary.service.SummaryService;
import com.zx.consultant.trace.service.TraceService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 知识库总结实现：先分别总结各 READY 文档，再聚合生成知识库级最终总结。
 */
@Slf4j
@Service
public class KnowledgeBaseSummaryServiceImpl implements KnowledgeBaseSummaryService {
    private final SummaryService summaryService;
    private final SummaryDocumentLoader documentLoader;
    private final MemoryService memoryService;
    private final TraceService traceService;
    public KnowledgeBaseSummaryServiceImpl(SummaryService summaryService,
                                           SummaryDocumentLoader documentLoader,
                                           MemoryService memoryService,
                                           TraceService traceService) {
        this.summaryService = summaryService;
        this.documentLoader = documentLoader;
        this.memoryService = memoryService;
        this.traceService = traceService;
    }

    /**
     * 知识库总结实现：先分别总结各 READY 文档，再聚合生成知识库级最终总结。
     */
    @Override
    public WorkflowContext summarize(WorkflowContext context) {
        context.setTraceId(TraceContext.getTraceId());
        log.info("=== 开始执行知识库总结，traceId={}, ConversationID: {} ===",
                context.getTraceId(), context.getConversationId());
        long startTime = System.currentTimeMillis();

        try {
            context.setCitations(Collections.emptyList());
            context.setNeedsClarification(false);
            try {
                if (context.getKnowledgeId() == null) {
                    return complete(context, "会话未绑定知识库，无法总结");
                }
                //获取已就绪文档
                List<Document> readyDocs = new ArrayList<>();
                TraceRecorder.record("Load Documents", () ->
                        readyDocs.addAll(documentLoader.listReadyDocuments(context.getKnowledgeId())));
                log.info("知识库总结, knowledgeId={}, readyCount={}", context.getKnowledgeId(), readyDocs.size());
                if (readyDocs.isEmpty()) {
                    return complete(context, "当前知识库暂无已就绪的文档，无法进行总结");
                }
                //分别总结各 READY 文档
                List<String> documentSummaries = new ArrayList<>();
                for (Document document : readyDocs) {
                    try {
                        TraceRecorder.record("Summarize Document", () -> {
                            String docSummary;
                            //判断文档是否为短文档
                            if (documentLoader.isShortDocument(document)) {
                                String text = documentLoader.loadDocumentText(document);
                                if (text == null || text.isBlank()) {
                                    log.warn("跳过空文档, documentId={}, fileName={}", document.getId(), document.getFileName());
                                    return;
                                }
                                docSummary = summaryService.summarizeDocumentText(
                                        text, "请概括文档《" + document.getFileName() + "》的主题与要点。");
                            } else {
                                List<String> chunks = documentLoader.loadDocumentChunks(document);
                                if (chunks.isEmpty()) {
                                    log.warn("跳过空文档, documentId={}, fileName={}", document.getId(), document.getFileName());
                                    return;
                                }
                                docSummary = summaryService.summarizeDocumentText(
                                        chunks, "请概括文档《" + document.getFileName() + "》的主题与要点。");
                            }
                            documentSummaries.add("【" + document.getFileName() + "】\n" + docSummary);
                        });
                    } catch (Exception e) {
                        log.warn("单篇文档摘要失败, documentId={}, fileName={}",
                                document.getId(), document.getFileName(), e);
                    }
                }
                //如果已就绪文档内容为空，则返回错误信息
                if (documentSummaries.isEmpty()) {
                    return complete(context, "知识库中的已就绪文档内容为空，无法进行总结");
                }
                //聚合生成知识库级最终总结
                String aggregated = String.join("\n\n", documentSummaries);
                String[] answerHolder = new String[1];
                //调用总结服务生成知识库级最终总结
                TraceRecorder.record("Summarize Knowledge Base 最终总结", () ->
                        answerHolder[0] = summaryService.summarizeKnowledgeBase(aggregated, context.getOriginalQuery()));
                //返回总结结果
                return complete(context, answerHolder[0]);
            } catch (Exception e) {
                log.error("知识库总结失败, knowledgeId={}", context.getKnowledgeId(), e);
                return complete(context, "知识库总结服务暂时不可用，请稍后重试");
            }
        } finally {
            // 无论成功失败，都将 Span 回写 Context、落库并打印摘要
            context.setNodeSpans(new ArrayList<>(TraceContext.getSpans()));
            persistTraceSpans(context);
            TraceRecorder.logSummary();
            log.info("=== 知识库总结结束，traceId={}, 总耗时: {} ms ===",
                    context.getTraceId(), System.currentTimeMillis() - startTime);
        }
    }


    /**
     * 完成总结：设置总结结果，并写入会话记忆
     */
    private WorkflowContext complete(WorkflowContext context, String answer) {
        context.setLlmResponse(answer);
        context.setFinalAnswer(answer);
        context.setCitations(Collections.emptyList());
        TraceRecorder.record("Memory Persist", () -> {
            if (context.getConversationId() != null
                    && context.getOriginalQuery() != null
                    && answer != null
                    && !answer.isBlank()) {
                memoryService.appendTurn(
                        context.getConversationId(),
                        context.getOriginalQuery(),
                        answer);
            }
        });
        return context;
    }

    /**
     * Trace 落库失败不影响主流程（总结结果仍可正常返回）
     */
    private void persistTraceSpans(WorkflowContext context) {
        try {
            traceService.saveSpans(context.getTraceId(), context.getNodeSpans());
        } catch (Exception e) {
            log.warn("Trace spans 落库失败, traceId={}, error={}",
                    context.getTraceId(), e.getMessage());
        }
    }
}