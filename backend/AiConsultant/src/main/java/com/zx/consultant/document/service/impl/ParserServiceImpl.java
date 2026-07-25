package com.zx.consultant.document.service.impl;

import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.document.dto.ParsedDocument;
import com.zx.consultant.document.service.ParserService;

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
            ParsedDocument result;
            switch (fileType.toLowerCase()) {
                case "txt":
                case "md":
                case "markdown":
                    result = parsePlainText(path, document);
                    break;
                case "pdf":
                    result = parsePdfByPage(path, document);
                    break;
                default:
                    throw new BaseException("不支持的文件格式: " + fileType);
            }
            return result;
            
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件解析发生未知异常: ", e);
            throw new BaseException("文档解析失败，请检查文件是否损坏");
        }
    }

    /**
     * 纯文本 / Markdown：整篇视为第 1 页
     */
    private ParsedDocument parsePlainText(Path path, Document document) {
        dev.langchain4j.data.document.Document lcDoc =
                FileSystemDocumentLoader.loadDocument(path, new TextDocumentParser());
        String text = lcDoc.text() != null ? lcDoc.text() : "";

        return baseParsedDocument(document)
                .setContent(text)
                .addPage(1, text);
    }

    /**
     * PDF：按页提取文本，保留真实页码供切块溯源
     */
    private ParsedDocument parsePdfByPage(Path path, Document document) throws Exception {
        try (PDDocument pdDocument = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = pdDocument.getNumberOfPages();
            StringBuilder fullText = new StringBuilder();

            ParsedDocument result = baseParsedDocument(document);

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(pdDocument);
                if (pageText == null) {
                    pageText = "";
                }
                result.addPage(page, pageText);
                fullText.append(pageText);
                if (page < totalPages) {
                    fullText.append('\n');
                }
            }

            result.setContent(fullText.toString());
            log.info("PDF 按页解析完成，共 {} 页，正文长度: {}", totalPages, fullText.length());
            return result;
        }
    }

    private ParsedDocument baseParsedDocument(Document document) {
        return new ParsedDocument()
                .addMetadata("documentId", document.getId())
                .addMetadata("knowledgeId", document.getKnowledgeId())
                .addMetadata("fileName", document.getFileName());
    }
}
