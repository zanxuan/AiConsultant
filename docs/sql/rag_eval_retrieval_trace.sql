-- RAG 评测检索链路明细。一条记录对应一个进入 RRF 候选集合的 chunk。
-- 在 application.yml 实际连接的库执行（当前为 ai_consultant）。
-- 只新建本表，不修改 rag_eval_run。不要在生产库直接执行未确认的脚本。

CREATE TABLE IF NOT EXISTS rag_eval_retrieval_trace (
    id                 BIGINT        NOT NULL COMMENT '主键，雪花 ID',
    run_id             BIGINT        NOT NULL COMMENT '评测运行 ID',
    case_id            INT           NOT NULL COMMENT '用例 ID',
    chunk_id           BIGINT        DEFAULT NULL COMMENT 'chunk ID，不保存正文',
    document_id        BIGINT        DEFAULT NULL COMMENT '文档 ID',
    page               INT           DEFAULT NULL COMMENT '页码',
    vector_rank        INT           DEFAULT NULL COMMENT '向量通道排名，从 1 开始；未进入则为空',
    vector_score       DOUBLE        DEFAULT NULL COMMENT '向量分',
    bm25_rank          INT           DEFAULT NULL COMMENT 'BM25 排名，从 1 开始；未进入则为空',
    bm25_score         DOUBLE        DEFAULT NULL COMMENT 'BM25 分',
    rrf_rank           INT           DEFAULT NULL COMMENT '过滤前 RRF 排名，从 1 开始',
    rrf_score          DOUBLE        DEFAULT NULL COMMENT 'RRF 融合分',
    filtered           TINYINT       NOT NULL DEFAULT 0 COMMENT '1 表示过滤前候选被丢弃',
    filter_reason      VARCHAR(255)  DEFAULT NULL COMMENT '真实过滤原因；未过滤为空',
    after_filter_rank  INT           DEFAULT NULL COMMENT '过滤后、截断前排名；未进入则为空',
    final_rank         INT           DEFAULT NULL COMMENT '最终 Hybrid TopK 排名；未进入则为空',
    eval_doc_rank      INT           DEFAULT NULL COMMENT 'chunk 去重后，文档在评测 TopK 中的排名',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_run_case (run_id, case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 评测检索链路明细';
