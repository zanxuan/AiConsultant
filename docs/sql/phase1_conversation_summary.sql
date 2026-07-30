-- Phase 1: 会话滚动摘要持久化（MySQL 真相源，Redis 为热缓存）
ALTER TABLE conversation
    ADD COLUMN summary TEXT NULL COMMENT '窗口外历史的滚动摘要' AFTER title,
    ADD COLUMN summary_until_id BIGINT NULL COMMENT '摘要已覆盖到的最大消息ID' AFTER summary;
