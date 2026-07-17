package com.zx.consultant.document.service;

import com.zx.consultant.document.entity.Document;
import com.zx.consultant.document.dto.ParsedDocument;

public interface ParserService {
    
    /**
     * 解析文档
     * @param document 数据库文档实体（包含去哪里读文件等信息）
     * @return ParsedDocument 业务层解析结果（不依赖任何特定 AI 框架）
     */
    ParsedDocument parse(Document document);
    //这里不应该用langchain4j的Document，因为两者的含义不同，4j里面的Document指的是解析完成的文件
    //不要过于依赖框架,将框架放在类内部的实现逻辑
    
}
