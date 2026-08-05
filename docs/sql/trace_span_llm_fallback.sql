-- Trace LLM 降级字段：已有库执行本脚本；新库可直接重建 docs/sql/trace_span.sql
ALTER TABLE trace_span
    ADD COLUMN model_used          VARCHAR(128)  DEFAULT NULL COMMENT '实际使用的模型名' AFTER error_message,
    ADD COLUMN fallback_triggered  TINYINT(1)    DEFAULT NULL COMMENT '是否触发主→副降级' AFTER model_used,
    ADD COLUMN fallback_reason     VARCHAR(1024) DEFAULT NULL COMMENT '降级原因' AFTER fallback_triggered;
