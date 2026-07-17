package com.zx.consultant.document.service.impl;

import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.document.dto.ParsedDocument;
import com.zx.consultant.document.service.ParserService;

// 仅在实现类内部引入 LangChain4j 的工具
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class ParserServiceImpl implements ParserService {

    @Override
    public ParsedDocument parse(Document document) {
        String storagePath = document.getStoragePath();
        String fileType = document.getFileType();
        
        log.info("开始解析文档，ID: {}, 名称: {}", document.getId(), document.getFileName());
        
        Path path = Paths.get(storagePath);
        if (!path.toFile().exists()) {
            throw new BaseException("磁盘文件丢失，解析失败");
        }

        try {
            // 1. 调用 LangChain4j 的工具进行底层解析
            dev.langchain4j.data.document.Document lcDoc;
            switch (fileType.toLowerCase()) {
                case "txt":
                case "md":
                case "markdown":
                    lcDoc = FileSystemDocumentLoader.loadDocument(path, new TextDocumentParser());
                    break;
                case "pdf":
                    lcDoc = FileSystemDocumentLoader.loadDocument(path, new ApachePdfBoxDocumentParser());
                    break;
                default:
                    throw new BaseException("不支持的文件格式: " + fileType);
            }
            
            // 2. 防腐层转换：将 LangChain4j 的结果映射到你自己的业务 DTO 中
            ParsedDocument result = new ParsedDocument()
                    .setContent(lcDoc.text())
                    .addMetadata("documentId", document.getId())
                    .addMetadata("knowledgeId", document.getKnowledgeId())
                    .addMetadata("fileName", document.getFileName());
            
            // 3. 返回纯净的业务对象，彻底切断外部依赖
            return result;
            
        } catch (Exception e) {
            log.error("文件解析发生未知异常: ", e);
            throw new BaseException("文档解析失败，请检查文件是否损坏");
        }
    }
}