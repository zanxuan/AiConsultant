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





