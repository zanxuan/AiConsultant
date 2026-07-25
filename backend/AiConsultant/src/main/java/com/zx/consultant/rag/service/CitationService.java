package com.zx.consultant.rag.service;

import java.util.List;

import com.zx.consultant.rag.dto.CitationDTO;
import com.zx.consultant.rag.entity.RetrievedChunk;

public interface CitationService {

    List<CitationDTO> extractCitations(String llmResponse, List<RetrievedChunk> retrievedChunks);
}
