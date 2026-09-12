package com.zx.consultant.orchestrator;

import com.zx.consultant.chat.service.CasualChatService;
import com.zx.consultant.orchestrator.enums.Intent;
import com.zx.consultant.orchestrator.enums.SummaryScope;
import com.zx.consultant.orchestrator.service.ClarificationReplyClassifier;
import com.zx.consultant.orchestrator.service.IntentService;
import com.zx.consultant.pending.enums.PendingTaskStatus;
import com.zx.consultant.pending.enums.PendingTaskType;
import com.zx.consultant.pending.model.PendingTask;
import com.zx.consultant.pending.service.PendingTaskService;
import com.zx.consultant.summary.service.DocumentSummaryService;
import com.zx.consultant.summary.service.KnowledgeBaseSummaryService;
import com.zx.consultant.workflow.context.WorkflowContext;
import com.zx.consultant.workflow.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;



/**
 *  根据意图分发任务。存在 PendingTask 时先判断本轮是澄清回答还是新问题，不把 conversation 锁进 Summary 模式。
 */
@Slf4j
@Service
public class Orchestrator {
    
    private final IntentService intentService;
    private final WorkflowService workflowService;
    private final DocumentSummaryService documentSummaryService;
    private final KnowledgeBaseSummaryService knowledgeBaseSummaryService;
    private final CasualChatService casualChatService;
    private final PendingTaskService pendingTaskService;
    private final ClarificationReplyClassifier clarificationReplyClassifier;

    public Orchestrator(IntentService intentService,
                        WorkflowService workflowService,
                        DocumentSummaryService documentSummaryService,
                        KnowledgeBaseSummaryService knowledgeBaseSummaryService,
                        CasualChatService casualChatService,
                        PendingTaskService pendingTaskService,
                        ClarificationReplyClassifier clarificationReplyClassifier) {
        this.intentService = intentService;
        this.workflowService = workflowService;
        this.documentSummaryService = documentSummaryService;
        this.knowledgeBaseSummaryService = knowledgeBaseSummaryService;
        this.casualChatService = casualChatService;
        this.pendingTaskService = pendingTaskService;
        this.clarificationReplyClassifier = clarificationReplyClassifier;
    }

    /**
     * 根据意图分发任务
     * @param context
     * @return
     */
    public WorkflowContext dispatch(WorkflowContext context) {
        //获取 PendingTask
        PendingTask pending = pendingTaskService.get(context.getConversationId());
        if (pending != null && pending.getTaskType() == PendingTaskType.SUMMARY_DOCUMENT) {
            // 知识库已换绑则旧任务失效，不能跨库恢复。
            if (pending.getKnowledgeId() != null
                    && context.getKnowledgeId() != null
                    && !pending.getKnowledgeId().equals(context.getKnowledgeId())) {
                log.info("PendingTask 与当前知识库不一致，结束任务, conversationId={}",
                        context.getConversationId());
                pendingTaskService.delete(context.getConversationId());
            
            } else if (pending.getStatus() == PendingTaskStatus.WAITING_FOR_DOCUMENT) {
                String currentInput = context.getOriginalQuery();
                if (clarificationReplyClassifier.isAnsweringClarification(pending, currentInput)) {
                    return resumeSummaryDocument(context, pending, currentInput);
                }
                log.info("用户提出新问题，结束 PendingTask 后走正常编排, conversationId={}",
                        context.getConversationId());
                pendingTaskService.delete(context.getConversationId());
            } else {
                pendingTaskService.delete(context.getConversationId());
            }
        }
        return dispatchByIntent(context);
    }


      /**
     * 无 PendingTask 或已结束任务后的正常意图分发。
     */
      private WorkflowContext dispatchByIntent(WorkflowContext context) {
        //确认意图
        IntentResult intentResult = intentService.detect(context.getOriginalQuery());
        context.setIntent(intentResult.getIntent().name());
        log.info("Orchestrator 按 Intent 分发: {}", intentResult);
        //RAG链路
        if (intentResult.getIntent() == Intent.RAG_QA) {
            return workflowService.run(context);
        }
        //总结链路
        if (intentResult.getIntent() == Intent.SUMMARY) {
            //走单文档总结链路
            if (intentResult.getSummaryScope() == SummaryScope.DOCUMENT) {
                //调用总结服务生成总结结果
                WorkflowContext result = documentSummaryService.summarize(context);
                //完成总结
                return finishDocumentSummary(result, context.getOriginalQuery());
            }
            //走多/长文档总结链路
            if (intentResult.getSummaryScope() == SummaryScope.KNOWLEDGE_BASE) {
                return knowledgeBaseSummaryService.summarize(context);
            }
        }
        //聊天链路
        return casualChatService.execute(context);
    }


    /**
     * 用原始总结请求 + 本轮澄清回答恢复 Summary，不把文件名单独当成新的 Chat/RAG。
     */
    private WorkflowContext resumeSummaryDocument(WorkflowContext context,
                                                  PendingTask pending,
                                                  String currentInput) {
        String resumeAnswer = resolveResumeAnswer(currentInput, pending.getCandidateFileNames());
        String reconstructed = reconstructQuery(pending.getOriginalQuery(), resumeAnswer);
        log.info("恢复单文档总结, originalQuery={}, clarificationAnswer={}, reconstructed={}",
                pending.getOriginalQuery(), currentInput, reconstructed);
        context.setOriginalQuery(reconstructed);
        context.setIntent(Intent.SUMMARY.name());
        WorkflowContext result = documentSummaryService.summarize(context);
        return finishDocumentSummary(result, pending.getOriginalQuery());
    }


    /**
     * 仍需澄清则创建/刷新 PendingTask；已产出最终回答则删除任务。
     * pendingOriginalQuery 始终是触发澄清的那句原始请求。
     */
    private WorkflowContext finishDocumentSummary(WorkflowContext result, String pendingOriginalQuery) {
        if (result.isNeedsClarification()) {
            //创建/刷新 PendingTask
            saveSummaryDocumentPendingTask(result, pendingOriginalQuery);
            return askUserToClarifyDocument(result);
        }
        //删除任务
        pendingTaskService.delete(result.getConversationId());
        return result;
    }

    /**
     * 创建/刷新 PendingTask
     */
    private void saveSummaryDocumentPendingTask(WorkflowContext context, String originalQuery) {
        PendingTask task = new PendingTask();
        task.setTaskType(PendingTaskType.SUMMARY_DOCUMENT);
        task.setOriginalQuery(originalQuery);
        task.setStatus(PendingTaskStatus.WAITING_FOR_DOCUMENT);
        //获取澄清候选文档文件名
        List<String> candidates = context.getClarificationCandidates();
        //设置澄清候选文档文件名
        task.setCandidateFileNames(candidates == null ? Collections.emptyList() : candidates);
        //设置知识库ID
        task.setKnowledgeId(context.getKnowledgeId());
        //保存任务
        pendingTaskService.save(context.getConversationId(), task);
    }



    /**
     * 用户用序号/字母点选时，换成对应文件名，便于硬匹配和写入 Memory。
     */
    private static String resolveResumeAnswer(String currentInput, List<String> candidates) {
        if (currentInput == null) {
            return "";
        }
        String input = currentInput.trim();
        if (candidates == null || candidates.isEmpty()) {
            return input;
        }
        if (input.matches("\\d+")) {
            int idx = Integer.parseInt(input);
            if (idx >= 1 && idx <= candidates.size()) {
                return candidates.get(idx - 1);
            }
        }
        if (input.matches("[A-Za-z]")) {
            int idx = Character.toUpperCase(input.charAt(0)) - 'A';
            if (idx >= 0 && idx < candidates.size()) {
                return candidates.get(idx);
            }
        }
        return input;
    }
    /** 恢复任务时拼接原始请求与澄清回答，Memory 也使用该关联文本，而不是只存文件名。 */
    private static String reconstructQuery(String originalQuery, String clarificationAnswer) {
        String original = originalQuery == null ? "" : originalQuery.trim();
        String answer = clarificationAnswer == null ? "" : clarificationAnswer.trim();
        if (original.isEmpty()) {
            return answer;
        }
        if (answer.isEmpty() || original.contains(answer)) {
            return original;
        }
        return original + " " + answer;
    }
    


    



    /**
     * 单文档总结无法定位目标时，不执行总结，要求用户澄清文件名或改为知识库总结。
     *  * 澄清话术不在此处写入 Redis Memory。
     */
    private WorkflowContext askUserToClarifyDocument(WorkflowContext context) {
        String message = "无法确定目标文档。当前知识库中有多篇已就绪文档，请指定要总结的文件名；"
                + "如果需要概括整个知识库，请直接说明。";
        context.setFinalAnswer(message);
        context.setLlmResponse(message);
        context.setCitations(Collections.emptyList());
        return context;
    }
}
