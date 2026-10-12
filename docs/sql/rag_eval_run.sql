-- RAG 评测运行记录。在 application.yml 实际连接的库执行（当前为 ai_consultant）。
-- 新库只执行建表。已有旧表时只执行文件末尾的 ALTER。

CREATE TABLE IF NOT EXISTS rag_eval_run (
    id                BIGINT        NOT NULL COMMENT '主键，雪花 ID',
    user_id           BIGINT        DEFAULT NULL COMMENT '发起用户；--eval 无登录时为空',
    knowledge_id      BIGINT        NOT NULL COMMENT '评测知识库 ID',
    model_name        VARCHAR(128)  DEFAULT NULL COMMENT '评测使用的向量模型',
    knowledge_name    VARCHAR(100)  DEFAULT NULL COMMENT '评测当时的知识库名称',
    dataset_source    VARCHAR(32)   NOT NULL COMMENT 'GOLDEN_SET / UPLOAD',
    dataset_name      VARCHAR(255)  NOT NULL COMMENT '文件名或来源名',
    dataset_snapshot  JSON          NOT NULL COMMENT '实际参与计算的 EvalCase 列表',
    status            VARCHAR(16)   NOT NULL COMMENT 'RUNNING / SUCCESS / FAILED',
    total_cases       INT           DEFAULT NULL COMMENT '用例数',
    hit_rate          DOUBLE        DEFAULT NULL COMMENT 'Hit Rate@5，0~1',
    recall_at_k       DOUBLE        DEFAULT NULL COMMENT 'Recall@5，0~1',
    mrr               DOUBLE        DEFAULT NULL COMMENT 'MRR',
    avg_latency_ms    BIGINT        DEFAULT NULL COMMENT '检索耗时算术平均',
    p95_latency_ms    BIGINT        DEFAULT NULL COMMENT '检索耗时 P95',
    failed_count      INT           DEFAULT NULL COMMENT '失败用例数',
    duration_ms       BIGINT        DEFAULT NULL COMMENT '整次评测墙钟耗时',
    config_snapshot   JSON          NOT NULL COMMENT 'topK 与 retriever 配置',
    result_snapshot   JSON          DEFAULT NULL COMMENT '完整 EvalResult，含 cases 与 failedCases',
    error_message     VARCHAR(1024) DEFAULT NULL COMMENT '失败信息，超长截断',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    end_time          DATETIME      DEFAULT NULL COMMENT '结束时间',
    PRIMARY KEY (id),
    KEY idx_knowledge_create (knowledge_id, create_time),
    KEY idx_user_create (user_id, create_time),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 评测运行记录';

-- 已有表补充模型名和知识库名称。新库执行上面的建表语句后，这两列已存在，不必再执行。
ALTER TABLE rag_eval_run
    ADD COLUMN model_name VARCHAR(128) DEFAULT NULL COMMENT '评测使用的向量模型' AFTER knowledge_id,
    ADD COLUMN knowledge_name VARCHAR(100) DEFAULT NULL COMMENT '评测当时的知识库名称' AFTER model_name;
