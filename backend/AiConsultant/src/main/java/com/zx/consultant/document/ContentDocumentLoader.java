package com.zx.consultant.document;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContentDocumentLoader {

    public List<Document> load() {
        return ClassPathDocumentLoader.loadDocuments("content", new ApachePdfBoxDocumentParser());
    }
}
