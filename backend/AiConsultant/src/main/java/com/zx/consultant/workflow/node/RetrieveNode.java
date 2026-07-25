package com.zx.consultant.workflow.node;


import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.service.RetrieverService;
import com.zx.consultant.workflow.context.WorkflowContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 检索节点
 * @author zx
 * @date 2026-07-20
 * RetrieveNode
 */
@Component
@Order(3) // 定义节点顺序
public class RetrieveNode implements WorkflowNode {

    private final RetrieverService retrieverService;

    public RetrieveNode(RetrieverService retrieverService) {
        this.retrieverService = retrieverService;
    }

    @Override
    public void execute(WorkflowContext context) {
        // 使用重写后的 Query 进行检索，并按会话绑定的知识库隔离
        String queryToSearch = context.getRewrittenQuery() != null ? context.getRewrittenQuery() : context.getOriginalQuery();
        List<RetrievedChunk> docs = retrieverService.retrieve(queryToSearch, context.getKnowledgeId());
        context.setRetrievedDocuments(docs);
    }

    @Override
    public String getName() {
        //return "Hybrid Search Node";
        return "Retrieve Node";
    }
}