CREATE DATABASE IF NOT EXISTS knowledge_platform
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE knowledge_platform;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='用户表';


ALTER TABLE `user`
ADD COLUMN `role` TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通用户，1-管理员';




CREATE TABLE `knowledge_base` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '知识库描述',
    `document_count` INT NOT NULL DEFAULT 0 COMMENT '文档数量',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),

    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_name` (`name`)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='知识库表';



CREATE TABLE document (
    id BIGINT NOT NULL COMMENT '主键ID',

    knowledge_id BIGINT NOT NULL COMMENT '所属知识库ID',

    file_name VARCHAR(255) NOT NULL COMMENT '文件名称',

    file_type VARCHAR(20) NOT NULL COMMENT '文件类型（PDF、MD、TXT等）',

    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',

    storage_path VARCHAR(500) NOT NULL COMMENT '文件存储路径',

    status VARCHAR(20) NOT NULL COMMENT '文档状态：UPLOADING、PARSING、INDEXING、READY、FAILED',

    chunk_count INT NOT NULL DEFAULT 0 COMMENT '切块数量',

    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),

    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_status (status)

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='文档信息表';


CREATE TABLE chunk (
    id BIGINT NOT NULL COMMENT '主键ID',

    document_id BIGINT NOT NULL COMMENT '所属文档ID',

    chunk_index INT NOT NULL COMMENT '切块序号',

    page INT DEFAULT NULL COMMENT '页码（PDF文档）',

    content TEXT NOT NULL COMMENT '切块内容',

    token_size INT NOT NULL COMMENT 'Token数量',

    vector_id VARCHAR(255) NOT NULL COMMENT '向量数据库中的ID',

    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),

    INDEX idx_vector_id (vector_id),
    INDEX idx_document_chunk (document_id, chunk_index)

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='文档切块表';



ALTER TABLE chunk
MODIFY COLUMN vector_id VARCHAR(255) NULL;


CREATE TABLE conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    knowledge_id BIGINT COMMENT '关联知识库ID',

    title VARCHAR(255) COMMENT '会话标题',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'

) COMMENT='聊天会话表';


CREATE TABLE message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',

    conversation_id BIGINT NOT NULL COMMENT '所属会话ID',

    role VARCHAR(20) NOT NULL COMMENT '消息角色(user/assistant)',

    content TEXT NOT NULL COMMENT '消息内容',

    reference TEXT COMMENT '引用来源(JSON格式)',

    token_usage INT COMMENT 'token消耗',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_conversation_id(conversation_id)

) COMMENT='聊天消息表';





-- Workflow 节点级 Trace 落库（新建表，不改动既有表结构）
CREATE TABLE IF NOT EXISTS trace_span (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    trace_id      VARCHAR(64)  NOT NULL COMMENT '请求链路ID',
    node_name     VARCHAR(128) NOT NULL COMMENT '节点名称',
    cost_time     BIGINT       NOT NULL COMMENT '执行耗时(ms)',
    status        VARCHAR(32)  NOT NULL COMMENT 'SUCCESS / FAILED',
    error_message VARCHAR(1024)         DEFAULT NULL COMMENT '失败异常信息',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow节点级Trace记录';

-- Trace LLM 降级字段：已有库执行本脚本；新库可直接重建 docs/sql/trace_span.sql
ALTER TABLE trace_span
    ADD COLUMN model_used          VARCHAR(128)  DEFAULT NULL COMMENT '实际使用的模型名' AFTER error_message,
    ADD COLUMN fallback_triggered  TINYINT(1)    DEFAULT NULL COMMENT '是否触发主→副降级' AFTER model_used,
    ADD COLUMN fallback_reason     VARCHAR(1024) DEFAULT NULL COMMENT '降级原因' AFTER fallback_triggered;


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

