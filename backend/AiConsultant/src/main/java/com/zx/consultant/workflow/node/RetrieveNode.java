package com.zx.consultant.workflow.node;


import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.retriever.HybridRetriever;
import com.zx.consultant.rag.service.RetrieverService;
import com.zx.consultant.workflow.context.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

/**
 * 检索节点：Hybrid Search 后做简单结果判断，无相关知识时短路友好回复，不进入 Prompt/LLM。
 */
@Slf4j
@Component
@Order(3) // 定义节点顺序
public class RetrieveNode implements WorkflowNode {

    private static final String MSG_NO_RESULT = "知识库中暂未找到相关信息";
    private static final String MSG_LOW_SCORE = "当前知识库暂无相关内容";

    private final RetrieverService retrieverService;
    private final HybridRetriever hybridRetriever;

    public RetrieveNode(RetrieverService retrieverService, HybridRetriever hybridRetriever) {
        this.retrieverService = retrieverService;
        this.hybridRetriever = hybridRetriever;
    }

    @Override
    public void execute(WorkflowContext context) {
        // 使用重写后的 Query 进行检索，并按会话绑定的知识库隔离
        String queryToSearch = context.getRewrittenQuery() != null ? context.getRewrittenQuery() : context.getOriginalQuery();
        List<RetrievedChunk> docs = retrieverService.retrieve(queryToSearch, context.getKnowledgeId());

        // 情况1：没有结果 → 不拼空资料进 Prompt，直接友好回复
        if (hybridRetriever.isEmpty(docs)) {
            earlyStop(context, MSG_NO_RESULT);
            return;
        }

        // 情况2：最高向量分低于 min-score → 视为无相关知识
        if (hybridRetriever.isScoreTooLow(docs)) {
            earlyStop(context, MSG_LOW_SCORE);
            return;
        }

        context.setRetrievedDocuments(docs);
    }

    private void earlyStop(WorkflowContext context, String message) {
        log.info("检索短路: {}, conversationId={}, knowledgeId={}",
                message, context.getConversationId(), context.getKnowledgeId());
        context.setRetrievedDocuments(Collections.emptyList());
        context.setCitations(Collections.emptyList());
        context.setFinalAnswer(message);
        context.setEarlyStop(true);
    }

    @Override
    public String getName() {
        //return "Hybrid Search Node";
        return "Retrieve Node";
    }
}
