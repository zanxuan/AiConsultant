package com.zx.consultant.document.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/**
 * 解析后的文档 DTO（隔离业务层与 AI 框架的防腐层对象）
 */
@Data
@Accessors(chain = true)
public class ParsedDocument {
    
    /**
     * 解析提取出的纯文本正文
     */
    private String content;
    
    /**
     * 文档元数据（如 documentId, knowledgeId, fileName 等）
     * 以后无论换什么 AI 框架，这些元数据都能无缝转换
     */
    private Map<String, Object> metadata = new HashMap<>();
    
    // 提供一个便捷添加元数据的方法
    public ParsedDocument addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
}