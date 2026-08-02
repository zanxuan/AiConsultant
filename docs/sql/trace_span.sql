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
