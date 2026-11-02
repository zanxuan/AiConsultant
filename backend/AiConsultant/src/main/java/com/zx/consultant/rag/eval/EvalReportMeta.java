package com.zx.consultant.rag.eval;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 写入 Markdown 报告时的运行信息。不进入 {@link EvalResult}，避免改结果快照。
 */
@Getter
@RequiredArgsConstructor
public class EvalReportMeta {

    private final Long runId;

    private final String datasetName;

    private final RagEvalDatasetSource datasetSource;

    private final Long knowledgeId;

    private final String knowledgeName;
}
