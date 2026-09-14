package com.zx.consultant.summary.service.impl;
import com.zx.consultant.common.trace.TraceContext;
import com.zx.consultant.common.trace.TraceRecorder;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.memory.service.MemoryService;
import com.zx.consultant.summary.service.DocumentSummaryService;
import com.zx.consultant.summary.service.SummaryDocumentLoader;
import com.zx.consultant.summary.service.SummaryService;
import com.zx.consultant.trace.service.TraceService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 单文档总结实现：先硬匹配文件名，未完全命中再交给问答 LLM 按候选名称匹配，不猜测用户要哪一篇。
 */
@Slf4j
@Service
public class DocumentSummaryServiceImpl implements DocumentSummaryService {
    /** 目标不明确时的固定提示，由编排层据此触发追问，不写入会话记忆。 */
    static final String UNDETERMINED_TARGET = "无法确定目标文档";


    /** 解析 MATCH(A) / MATCH（B）等规定格式，兼容全角括号与多余空白。 */
    private static final Pattern MATCH_PATTERN = Pattern.compile(
            "MATCH\\s*[\\(（]\\s*([A-Za-z]+)\\s*[\\)）]",
            Pattern.CASE_INSENSITIVE);

    /** 文档匹配器系统提示：只根据文件名匹配，禁止按内容推测。 */
    private static final String MATCHER_SYSTEM_PROMPT = """
            你是一个文档匹配器。
            请根据用户的原始请求，判断用户想要总结哪个文档。
            注意：
            1. 只能根据候选文档名称进行匹配。
            2. 可以处理用户对文档名称的简称、口语表达、部分名称、轻微错别字。
            3. 如果某个候选文档明显对应用户请求，则选择该文档。
            4. 如果多个候选文档都可能符合用户请求，无法唯一确定，则返回 CLARIFY。
            5. 如果没有任何候选文档与用户请求对应，则返回 NOT_FOUND。
            6. 不要根据文档内容进行推测。
            7. 不要解释原因，只能返回规定格式。
            8. 如果用户只是说“总结一下”“帮我总结这个”“总结项目设计”等，而候选文档无法唯一确定，不要猜，返回 CLARIFY。
            """;
    private final SummaryService summaryService;
    private final SummaryDocumentLoader documentLoader;
    private final MemoryService memoryService;
    private final LLMService llmService;
    private final TraceService traceService;
    public DocumentSummaryServiceImpl(SummaryService summaryService,
                                      SummaryDocumentLoader documentLoader,
                                      MemoryService memoryService,
                                      LLMService llmService,
                                      TraceService traceService) {
        this.summaryService = summaryService;
        this.documentLoader = documentLoader;
        this.memoryService = memoryService;
        this.llmService = llmService;
        this.traceService = traceService;
    }

    /**
     * 按规则定位唯一目标文档并生成总结。目标不明确时只标记追问，不猜测文档。
     */
    @Override
    public WorkflowContext summarize(WorkflowContext context) {
        context.setTraceId(TraceContext.getTraceId());
        log.info("=== 开始执行单文档总结，traceId={}, ConversationID: {} ===",
                context.getTraceId(), context.getConversationId());
        long startTime = System.currentTimeMillis();

        try {
            context.setCitations(Collections.emptyList());
            // 目标不明确时只标记追问，不猜测文档。
            context.setNeedsClarification(false);

            try {
                if (context.getKnowledgeId() == null) {
                    return complete(context, "会话未绑定知识库，无法总结");
                }
                // 加载已就绪的文档
                List<Document> readyDocs = new ArrayList<>();
                TraceRecorder.record("Load Documents", () ->
                        readyDocs.addAll(documentLoader.listReadyDocuments(context.getKnowledgeId())));
                if (readyDocs.isEmpty()) {
                    return complete(context, "当前知识库暂无已就绪的文档，无法进行总结");
                }
                // 解析目标文档：先硬匹配，未命中再走 LLM 匹配。
                TargetResolution[] resolutionHolder = new TargetResolution[1];
                TraceRecorder.record("Resolve Target", () ->
                        resolutionHolder[0] = resolveTarget(context.getOriginalQuery(), readyDocs));
                TargetResolution resolution = resolutionHolder[0];

                // 无法唯一确定目标时交由编排层追问并创建 PendingTask，此处不写入 Redis Memory。
                if (resolution.needsClarification) {
                    log.info("单文档总结无法确定目标, knowledgeId={}, readyCount={}",
                            context.getKnowledgeId(), readyDocs.size());
                    context.setNeedsClarification(true);
                    //设置需要澄清的候选文档文件名
                    context.setClarificationCandidates(toFileNames(readyDocs));
                    //设置澄清话术
                    context.setFinalAnswer(buildClarificationMessage(readyDocs));
                    //返回上下文
                    return context;
                }

                // 没有任何候选文档对应请求时直接报错，不猜测文档。
                if (resolution.errorMessage != null) {
                    return complete(context, resolution.errorMessage);
                }


                // 先按 fileSize 判断长短：短文档加载原文一次总结，长文档复用已入库 Chunk。
                Document document = resolution.document;
                String[] answerHolder = new String[1];
                //判断文档是否为短文档
                if (documentLoader.isShortDocument(document)) {
                    //加载文档原文
                    String[] textHolder = new String[1];
                    TraceRecorder.record("Load Content", () ->
                            textHolder[0] = documentLoader.loadDocumentText(document));
                    String text = textHolder[0];
                    //如果文档原文为空，则返回错误信息
                    if (text == null || text.isBlank()) {
                        return complete(context, "文档《" + document.getFileName() + "》内容为空，无法总结");
                    }
                    log.info("执行短文档总结, documentId={}, fileName={}, fileSize={}",
                            document.getId(), document.getFileName(), document.getFileSize());
                    //调用总结服务生成短文档总结
                    TraceRecorder.record("Summarize Document", () ->
                            answerHolder[0] = summaryService.summarizeDocumentText(text, context.getOriginalQuery()));

                } else {
                    List<String> chunks = new ArrayList<>();
                    TraceRecorder.record("Load Content", () ->
                            chunks.addAll(documentLoader.loadDocumentChunks(document)));
                    if (chunks.isEmpty()) {
                        return complete(context, "文档《" + document.getFileName() + "》内容为空，无法总结");
                    }
                    log.info("执行长文档总结, documentId={}, fileName={}, fileSize={}, chunkCount={}",
                            document.getId(), document.getFileName(), document.getFileSize(), chunks.size());
                    //调用总结服务生成长文档总结
                    TraceRecorder.record("Summarize Document", () ->
                            answerHolder[0] = summaryService.summarizeDocumentText(chunks, context.getOriginalQuery()));
                }
                //返回总结结果
                return complete(context, answerHolder[0]);

            } catch (Exception e) {
                log.error("单文档总结失败, knowledgeId={}", context.getKnowledgeId(), e);
                return complete(context, "文档总结服务暂时不可用，请稍后重试");
            }
        } finally {
            // 无论成功失败，都将 Span 回写 Context、落库并打印摘要
            context.setNodeSpans(new ArrayList<>(TraceContext.getSpans()));
            persistTraceSpans(context);
            TraceRecorder.logSummary();
            log.info("=== 单文档总结结束，traceId={}, 总耗时: {} ms ===",
                    context.getTraceId(), System.currentTimeMillis() - startTime);
        }
    }


     /**
     * 解析目标文档：文件名完全命中且唯一则直接选用；多篇硬匹配则追问；
     * 否则把全部 READY 文档名交给问答 LLM 匹配。
     */
     TargetResolution resolveTarget(String query, List<Document> readyDocs) {
        List<Document> namedMatches = new ArrayList<>();
        for (Document document : readyDocs) {
            // 问句是否完全命中该文件名
            if (hardMatchFileName(query, document.getFileName())) {
                namedMatches.add(document);
            }
        }
        if (namedMatches.size() == 1) {
            log.info("单文档总结硬匹配命中, fileName={}", namedMatches.get(0).getFileName());
            return TargetResolution.ofDocument(namedMatches.get(0));
        }
        // 多篇同时完全命中时不猜测，交由用户澄清。
        if (namedMatches.size() > 1) {
            log.info("单文档总结硬匹配多篇命中, matchCount={}", namedMatches.size());
            return TargetResolution.clarification();
        }
        // 硬匹配未命中，把知识库全部 READY 文档名交给问答 LLM 做名称匹配。
        return matchByLlm(query, readyDocs);
    }



    /**
     * 硬匹配：问句包含完整文件名（含扩展名），或包含去掉扩展名后的完整主干（主干至少 2 个字符）。
     * 不接受简称、部分名称或错别字，那些交给 LLM 匹配。
     */
    private boolean hardMatchFileName(String query, String fileName) {
        if (query == null || query.isBlank() || fileName == null || fileName.isBlank()) {
            return false;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String normalizedName = fileName.toLowerCase(Locale.ROOT).trim();
        if (normalizedQuery.contains(normalizedName)) {
            return true;
        }
        String stem = stripExtension(normalizedName);
        return stem.length() >= 2 && normalizedQuery.contains(stem);
    }


    /**
     * 调用问答 LLM，仅根据候选文档名称判断用户想总结哪一篇。
     */
    private TargetResolution matchByLlm(String query, List<Document> readyDocs) {
        // 给候选文档编号
        Map<String, Document> labeledDocs = labelDocuments(readyDocs);
        String candidateBlock = buildCandidateBlock(labeledDocs);
        String allowedOutputs = buildAllowedOutputs(labeledDocs);
        // 构建提示词
        // 构建请求
        PromptRequest request = new PromptRequest();
        request.setSystemPrompt(MATCHER_SYSTEM_PROMPT + "\n只能返回以下结果之一：\n\n" + allowedOutputs);
        request.setContext("候选文档：\n" + candidateBlock);
        request.setUserQuery(query == null ? "" : query);
        String raw;
        try {
            raw = llmService.generateAnswer(request);
        } catch (Exception e) {
            log.warn("文档匹配模型调用失败，回退为追问, readyCount={}", readyDocs.size(), e);
            return TargetResolution.clarification();
        }
        // 解析匹配器输出
        TargetResolution parsed = parseMatcherOutput(raw, labeledDocs, readyDocs);
        log.info("单文档总结 LLM 匹配结果: {}, 模型原始输出: {}", describeResolution(parsed), raw);
        return parsed;
    }

    /** 按 A、B、C… 给候选文档编号，超过 26 篇时使用 AA、AB。 */
    private static Map<String, Document> labelDocuments(List<Document> readyDocs) {
        Map<String, Document> labeled = new LinkedHashMap<>();
        for (int i = 0; i < readyDocs.size(); i++) {
            labeled.put(toLabel(i), readyDocs.get(i));
        }
        return labeled;
    }


    /** 构建候选文档块 */
    private static String buildCandidateBlock(Map<String, Document> labeledDocs) {
        StringBuilder block = new StringBuilder();
        // 遍历候选文档
        for (Map.Entry<String, Document> entry : labeledDocs.entrySet()) {
            // 如果候选文档块不为空，则添加换行符
            if (!block.isEmpty()) {
                block.append("\n");
            }
            // 获取候选文档文件名
            String fileName = entry.getValue().getFileName();
            // 添加候选文档编号和文件名
            block.append(entry.getKey()).append("：").append(fileName == null ? "" : fileName);
        }
        // 返回候选文档块
        return block.toString();
    }

    /** 构建允许的输出 */
    private static String buildAllowedOutputs(Map<String, Document> labeledDocs) {
        StringBuilder allowed = new StringBuilder();
        // 遍历候选文档
        for (String label : labeledDocs.keySet()) {
            // 添加允许的输出
            allowed.append("MATCH(").append(label).append(")\n");
        }
        // 添加允许的输出
        allowed.append("CLARIFY\nNOT_FOUND");
        // 返回允许的输出
        return allowed.toString();
    }


    /**
     * 解析匹配器输出：MATCH(X) 选用对应文档，NOT_FOUND 报错，其余（含 CLARIFY 或无法解析）一律追问。
     */
    private TargetResolution parseMatcherOutput(String raw,
                                               Map<String, Document> labeledDocs,
                                               List<Document> readyDocs) {
        if (raw == null || raw.isBlank()) {
            return TargetResolution.clarification();
        }
        // 解析匹配器输出
        Matcher matcher = MATCH_PATTERN.matcher(raw);
        if (matcher.find()) {
            String label = matcher.group(1).toUpperCase(Locale.ROOT);
            Document document = labeledDocs.get(label);
            if (document != null) {
                return TargetResolution.ofDocument(document);
            }
            log.warn("文档匹配器返回了未知编号, label={}", label);
            return TargetResolution.clarification();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("NOT_FOUND")) {
            return TargetResolution.error(buildNotFoundMessage(readyDocs));
        }
        return TargetResolution.clarification();
    }


    /** 0 → A，25 → Z，26 → AA。 */
    private static String toLabel(int index) {
        StringBuilder label = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            n--;
            label.append((char) ('A' + (n % 26)));
            n /= 26;
        }
        return label.reverse().toString();
    }
    /** 去掉文件扩展名 */
    private static String stripExtension(String fileName) {
        // 获取文件扩展名的起始位置
        int dot = fileName.lastIndexOf('.');
        // 如果文件扩展名存在，则返回去掉扩展名后的文件名
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 将已就绪文档文件名列表转换为字符串列表 */
    private static List<String> toFileNames(List<Document> readyDocs) {
        List<String> names = new ArrayList<>();
        for (Document document : readyDocs) {
            names.add(document.getFileName() == null ? "" : document.getFileName());
        }
        return names;
    }


    /** 追问话术列出已就绪文档，方便用户指定文件名。 */
    private static String buildClarificationMessage(List<Document> readyDocs) {
        StringBuilder message = new StringBuilder(UNDETERMINED_TARGET);
        message.append("。当前知识库中已就绪的文档有：\n");
        for (int i = 0; i < readyDocs.size(); i++) {
            String fileName = readyDocs.get(i).getFileName();
            message.append(i + 1).append(". ").append(fileName == null ? "" : fileName).append("\n");
        }
        message.append("请指定要总结的文件名；如果需要概括整个知识库，请直接说明。");
        return message.toString();
    }


    /** NOT_FOUND 时告知用户当前有哪些已就绪文档，避免空报错。 */
    private static String buildNotFoundMessage(List<Document> readyDocs) {
        StringBuilder message = new StringBuilder("未找到与当前请求匹配的已就绪文档。");
        if (readyDocs != null && !readyDocs.isEmpty()) {
            message.append("当前已就绪文档有：");
            for (int i = 0; i < readyDocs.size(); i++) {
                if (i > 0) {
                    message.append("、");
                }
                String fileName = readyDocs.get(i).getFileName();
                message.append(fileName == null ? "" : fileName);
            }
            message.append("。");
        }
        return message.toString();
    }

    /** 描述目标解析结果 */
    private static String describeResolution(TargetResolution resolution) {
        if (resolution.document != null) {
            return "MATCH(" + resolution.document.getFileName() + ")";
        }
        if (resolution.errorMessage != null) {
            return "NOT_FOUND";
        }
        return "CLARIFY";
    }


    /** 写入最终回答并落会话记忆，用于成功、空内容、未找到文档等可直接回复的场景。 */
    private WorkflowContext complete(WorkflowContext context, String answer) {
        context.setLlmResponse(answer);
        context.setFinalAnswer(answer);
        context.setCitations(Collections.emptyList());
        persistMemory(context, answer);
        return context;
    }
    
    /** 写入会话记忆：仅成功/错误等可直接回复的场景。澄清不走这里。恢复任务时 originalQuery 已含原始请求+指定文档。 */
    private void persistMemory(WorkflowContext context, String answer) {
        TraceRecorder.record("Memory Persist", () -> {
            if (context.getConversationId() != null
                    && context.getOriginalQuery() != null
                    && answer != null
                    && !answer.isBlank()) {
                // 写入会话记忆
                memoryService.appendTurn(
                        context.getConversationId(),
                        context.getOriginalQuery(),
                        answer);
            }
        });
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
    /** 目标解析结果：命中文档、需要追问、或明确错误，三者互斥。 */
    static final class TargetResolution {
        final Document document;
        final boolean needsClarification;
        final String errorMessage;
        private TargetResolution(Document document, boolean needsClarification, String errorMessage) {
            this.document = document;
            this.needsClarification = needsClarification;
            this.errorMessage = errorMessage;
        }
        /** 命中文档 */
        static TargetResolution ofDocument(Document document) {
            return new TargetResolution(document, false, null);
        }
        /** 需要追问 */
        static TargetResolution clarification() {
            return new TargetResolution(null, true, null);
        }
        /** 明确错误 */
        static TargetResolution error(String message) {
            return new TargetResolution(null, false, message);
        }
    }
}