package com.zx.consultant.rag.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.dto.CitationDTO;
import com.zx.consultant.rag.service.CitationService;
import com.zx.consultant.document.service.DocumentService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CitationServiceImpl implements CitationService {

    private final DocumentService documentService;

    /**
     * 匹配 LLM 回答中的引用追踪标记：
     * - &lt;cite id="123"/&gt;
     * - &lt;reference_document id="123" ...&gt;（兼容旧格式）
     */
    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "<(?:cite|reference_document)\\s+id=\"(\\d+)\"",
            Pattern.CASE_INSENSITIVE);

    /**
     * @param llmResponse      大模型生成的完整回答（可含 cite 标记）
     * @param retrievedChunks  本次对话检索到的文档片段（包含了完整的 documentId 和 page）
     * @return 返回结构化的引用列表，方便前端渲染（如展示为参考资料列表）
     */
    @Override
    public List<CitationDTO> extractCitations(String llmResponse, List<RetrievedChunk> retrievedChunks) {
        List<CitationDTO> citations = new ArrayList<>();

        // 1. 没有检索资料则无法构建引用
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return citations;
        }

        // 2. 优先从回答中的 <cite id="..."/> 提取被引用的 documentId
        Set<Long> citedDocIds = new HashSet<>();
        if (llmResponse != null && !llmResponse.isBlank()) {
            Matcher matcher = CITATION_PATTERN.matcher(llmResponse);
            while (matcher.find()) {
                try {
                    citedDocIds.add(Long.parseLong(matcher.group(1)));
                } catch (NumberFormatException e) {
                    // 忽略解析错误的 ID
                }
            }
        }

        // 3. 有 cite 标记：返回被点名文档下、本次检索到的各页；无标记：兜底返回全部检索结果
        // 去重键为 documentId + page，避免同文档多页被压成「只剩第 1 页」
        boolean fallbackAll = citedDocIds.isEmpty();
        Set<String> processedKeys = new HashSet<>();

        for (RetrievedChunk chunk : retrievedChunks) {
            Long docId = chunk.getDocumentId();
            if (docId == null) {
                continue;
            }
            if (!fallbackAll && !citedDocIds.contains(docId)) {
                continue;
            }

            String dedupeKey = docId + ":" + (chunk.getPage() != null ? chunk.getPage() : "null");
            if (!processedKeys.add(dedupeKey)) {
                continue;
            }

            String docName = documentService.getDocumentName(docId);
            citations.add(new CitationDTO(
                    docId,
                    docName,
                    chunk.getPage(),
                    chunk.getContent()));
        }

        return citations;
    }
}
