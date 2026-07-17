package com.zx.consultant.document.enums;

/**
 * 文档状态枚举
 */
public enum DocumentStatus {
    UPLOADING("上传中"),
    PARSING("解析中"),
    INDEXING("向量入库中"),
    READY("就绪"),
    FAILED("失败");

    private final String desc;

    DocumentStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}