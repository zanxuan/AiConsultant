package com.zx.consultant.summary.service;

import java.util.List;

/**
 * 总结执行器：短文档对原文一次总结，长文档复用已入库 Chunk 按 group-size 分组归纳。
 * 不负责切分文本，也不负责判断用户意图。
 */
public interface SummaryService {

    /**
     * 对短文档原文做一次总结，不再加载或切分 Chunk。
     */
    String summarizeDocumentText(String text, String userQuery);

    /**
     * 对长文档做总结：复用已入库 Chunk，按 group-size 分组归纳后再综合。
     */
    String summarizeDocumentText(List<String> chunks, String userQuery);

    /**
     * 将各文档摘要聚合成知识库级最终总结，不再对原文二次切块。
     */
    String summarizeKnowledgeBase(String aggregatedDocumentSummaries, String userQuery);
}
