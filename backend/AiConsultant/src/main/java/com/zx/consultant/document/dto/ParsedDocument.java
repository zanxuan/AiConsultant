package com.zx.consultant.document.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析后的文档 DTO（隔离业务层与 AI 框架的防腐层对象）
 */
@Data
@Accessors(chain = true)
public class ParsedDocument {
    
    /**
     * 解析提取出的纯文本正文（全文拼接，便于日志与兼容）
     */
    private String content;
    
    /**
     * 按页内容；无分页文件可只有一页（pageNumber=1）
     */
    private List<ParsedPage> pages = new ArrayList<>();
    
    /**
     * 文档元数据（如 documentId, knowledgeId, fileName 等）
     * 以后无论换什么 AI 框架，这些元数据都能无缝转换
     */
    private Map<String, Object> metadata = new HashMap<>();
    
    public ParsedDocument addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public ParsedDocument addPage(int pageNumber, String pageContent) {
        this.pages.add(new ParsedPage(pageNumber, pageContent));
        return this;
    }
}
