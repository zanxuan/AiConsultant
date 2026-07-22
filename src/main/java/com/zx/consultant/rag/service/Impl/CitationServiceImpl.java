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
// import com.zx.consultant.doc.mapper.DocumentMapper; // 假设你有这个用来查文件名

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CitationServiceImpl implements CitationService {

     private final DocumentService documentService;

    // 正则表达式：假设大模型按我们的 Prompt 输出了 "(文档ID: 12, 第 2 页)" 或者包含 "文档ID: 12" 的字眼
   // private static final Pattern CITATION_PATTERN = Pattern.compile("文档ID[:：]\\s*(\\d+)");
    // ✅ 修改后的正则表达式：匹配 <reference_document id="数字" 格式
private static final Pattern CITATION_PATTERN = Pattern.compile("<reference_document\\s+id=\"(\\d+)\"");

    /**
     * @param llmResponse      大模型生成的完整回答
     * @param retrievedChunks  本次对话检索到的文档片段（包含了完整的 documentId 和 page）
     * @return 返回结构化的引用列表，方便前端渲染（如展示为参考资料列表）
     */
    @Override
    public List<CitationDTO> extractCitations(String llmResponse, List<RetrievedChunk> retrievedChunks) {
        List<CitationDTO> citations = new ArrayList<>();
        
        // 1. 如果大模型回答没内容，或者根本没有检索到资料，直接返回空
        if (llmResponse == null || retrievedChunks == null || retrievedChunks.isEmpty()) {
            return citations;
        }

        // 2. 用正则从大模型回答中提取所有被引用的 documentId
        Set<Long> citedDocIds = new HashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(llmResponse);
        while (matcher.find()) {
            try {
                citedDocIds.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException e) {
                // 忽略解析错误的 ID
            }
        }

        // 3. 将提取到的 ID 与本次检索的 Chunks 进行匹配，生成给前端的对象
        Set<Long> processedDocIds = new HashSet<>(); // 去重，避免同一个文档多次出现在底部参考列表中
        
        for (RetrievedChunk chunk : retrievedChunks) {
            Long docId = chunk.getDocumentId();
            
            // 如果这个 chunk 的 docId 被大模型引用了，且还没有被加入到返回列表中
            if (citedDocIds.contains(docId) && !processedDocIds.contains(docId)) {
                
                // TODO: 拿着 docId 去数据库查真实的文件名
                String docName = documentService.getDocumentName(docId);
               // String mockDocName = "测试文档_" + docId + ".pdf"; // 模拟数据
                
                citations.add(new CitationDTO(docId, docName, chunk.getPage()));
                processedDocIds.add(docId);
            }
        }

        return citations;
    }
}