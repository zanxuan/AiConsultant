package com.zx.consultant.rag.service;

import java.util.List;

public interface CitationService {

    String extractCitations(String llmResponse, List<String> retrievedDocs);
}
