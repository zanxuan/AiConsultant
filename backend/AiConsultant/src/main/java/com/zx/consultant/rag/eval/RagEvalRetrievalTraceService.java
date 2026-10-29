package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.document.entity.Chunk;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.document.service.ChunkService;
import com.zx.consultant.document.service.DocumentService;
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import com.zx.consultant.rag.eval.mapper.RagEvalRetrievalTraceMapper;
import com.zx.consultant.rag.eval.vo.RagEvalRunDetailVO;
import com.zx.consultant.rag.eval.vo.EvalChunkContentVO;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO.RetrievalEvalDocVO;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO.RetrievalTraceChunkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 检索链路明细的批量保存和按用例查询。指标计算仍在 {@link RagEvalService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalRetrievalTraceService extends ServiceImpl<RagEvalRetrievalTraceMapper, RagEvalRetrievalTrace> {

    private final RagEvalRunService ragEvalRunService;
    private final DocumentService documentService;
    private final ChunkService chunkService;

    /**
     * 一次运行结束时整批替换。独立事务，避免和评测失败回滚缠在一起。
     * 先按 runId 删除，再插入，防止同一次运行重复写入。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRunTraces(Long runId, List<RagEvalRetrievalTrace> rows) {
        if (runId == null) {
            return;
        }
        this.remove(new LambdaQueryWrapper<RagEvalRetrievalTrace>()
                .eq(RagEvalRetrievalTrace::getRunId, runId));
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (RagEvalRetrievalTrace row : rows) {
            row.setId(null);
            row.setRunId(runId);
        }
        this.saveBatch(rows);
    }

    /**
     * 只读已保存的明细。校验记录归属，并确认用例属于这次运行。
     */
    public RagEvalRetrievalTraceVO getTrace(Long runId, Integer caseId, Long userId) {
        if (caseId == null) {
            throw new BaseException("评测用例不存在");
        }
        RagEvalRunDetailVO detail = ragEvalRunService.getDetail(runId, userId);
        if (!belongs(detail, caseId)) {
            throw new BaseException("评测用例不存在");
        }
        List<RagEvalRetrievalTrace> rows = this.list(new LambdaQueryWrapper<RagEvalRetrievalTrace>()
                .eq(RagEvalRetrievalTrace::getRunId, runId)
                .eq(RagEvalRetrievalTrace::getCaseId, caseId)
                .orderByAsc(RagEvalRetrievalTrace::getRrfRank)
                .orderByAsc(RagEvalRetrievalTrace::getId));
        RagEvalRetrievalTraceVO view = RetrievalTraceSnapshots.toView(
                runId, caseId, findCaseResult(detail, caseId), expectedDocIds(detail, caseId), rows);
        enrich(view);
        return view;
    }

    private boolean belongs(RagEvalRunDetailVO detail, Integer caseId) {
        if (detail.getCases() != null) {
            for (EvalCase item : detail.getCases()) {
                if (item != null && caseId.equals(item.getId())) {
                    return true;
                }
            }
        }
        if (findCaseResult(detail, caseId) != null) {
            return true;
        }
        EvalResult result = detail.getResult();
        if (result != null && result.getFailedCases() != null) {
            for (FailedCase failed : result.getFailedCases()) {
                if (failed != null && caseId.equals(failed.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private EvalCaseResult findCaseResult(RagEvalRunDetailVO detail, Integer caseId) {
        if (detail.getResult() == null || detail.getResult().getCases() == null) {
            return null;
        }
        for (EvalCaseResult item : detail.getResult().getCases()) {
            if (item != null && caseId.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    private List<String> expectedDocIds(RagEvalRunDetailVO detail, Integer caseId) {
        if (detail.getCases() != null) {
            for (EvalCase item : detail.getCases()) {
                if (item != null && caseId.equals(item.getId()) && item.getExpectedDocIds() != null) {
                    return item.getExpectedDocIds();
                }
            }
        }
        EvalCaseResult caseResult = findCaseResult(detail, caseId);
        if (caseResult != null && caseResult.getExpectedDocIds() != null) {
            return caseResult.getExpectedDocIds();
        }
        EvalResult result = detail.getResult();
        if (result != null && result.getFailedCases() != null) {
            for (FailedCase failed : result.getFailedCases()) {
                if (failed != null && caseId.equals(failed.getId()) && failed.getExpectedDocIds() != null) {
                    return failed.getExpectedDocIds();
                }
            }
        }
        return List.of();
    }

    private static final int CHUNK_CONTENT_LIMIT = 20;

    /**
     * 按 chunk 主键读取原文，供标准 Chunk 预览。
     * 只返回本次评测知识库里的切块，不读文档全文，也不改评测结果。
     */
    public List<EvalChunkContentVO> listChunkContents(Long runId, List<String> chunkIds, Long userId) {
        RagEvalRunDetailVO detail = ragEvalRunService.getDetail(runId, userId);
        List<String> requested = normalizeChunkIds(chunkIds);
        if (requested.size() > CHUNK_CONTENT_LIMIT) {
            throw new BaseException("一次最多读取 " + CHUNK_CONTENT_LIMIT + " 个 Chunk");
        }
        Map<String, Long> parsed = new LinkedHashMap<>();
        for (String chunkId : requested) {
            Long id = parseChunkId(chunkId);
            if (id != null) {
                parsed.put(chunkId, id);
            }
        }
        Map<Long, String> contents = loadOwnedContents(parsed.values(), detail.getKnowledgeId());
        List<EvalChunkContentVO> rows = new ArrayList<>();
        for (String chunkId : requested) {
            EvalChunkContentVO row = new EvalChunkContentVO();
            row.setChunkId(chunkId);
            Long id = parsed.get(chunkId);
            row.setContent(id == null ? null : contents.get(id));
            rows.add(row);
        }
        return rows;
    }

    /** 文档名和正文走现有文档、切块数据，明细表里没有全文。 */
    private void enrich(RagEvalRetrievalTraceVO view) {
        if (view == null || !view.isSaved()) {
            return;
        }
        List<RetrievalTraceChunkVO> chunks = allChunks(view);
        Map<Long, String> names = new HashMap<>();
        Map<Long, String> contents = loadContents(chunks);
        for (RetrievalTraceChunkVO chunk : chunks) {
            chunk.setDocumentName(documentName(names, chunk.getDocumentId()));
            if (chunk.getChunkId() != null) {
                chunk.setContent(contents.get(chunk.getChunkId()));
            }
        }
        for (RetrievalEvalDocVO doc : view.getEvalDocs()) {
            doc.setDocumentName(documentName(names, doc.getDocumentId()));
        }
    }

    private static List<String> normalizeChunkIds(List<String> chunkIds) {
        List<String> ids = new ArrayList<>();
        if (chunkIds == null) {
            return ids;
        }
        for (String raw : chunkIds) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split(",")) {
                String id = part.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** 只接受十进制整数字符串，不经过浮点数 */
    private static Long parseChunkId(String chunkId) {
        if (chunkId == null || !chunkId.matches("\\d{1,19}")) {
            return null;
        }
        try {
            return Long.parseLong(chunkId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 切块必须属于这次评测的知识库，避免凭 ID 读到其他库的原文 */
    private Map<Long, String> loadOwnedContents(Collection<Long> chunkIds, Long knowledgeId) {
        Map<Long, String> contents = new HashMap<>();
        if (chunkService == null || documentService == null || knowledgeId == null
                || chunkIds == null || chunkIds.isEmpty()) {
            return contents;
        }
        try {
            Collection<Chunk> stored = chunkService.listByIds(chunkIds);
            if (stored == null || stored.isEmpty()) {
                return contents;
            }
            Set<Long> documentIds = new HashSet<>();
            for (Chunk chunk : stored) {
                if (chunk != null && chunk.getDocumentId() != null) {
                    documentIds.add(chunk.getDocumentId());
                }
            }
            Map<Long, Long> knowledgeByDocument = new HashMap<>();
            if (!documentIds.isEmpty()) {
                Collection<Document> documents = documentService.listByIds(documentIds);
                if (documents != null) {
                    for (Document document : documents) {
                        if (document != null && document.getId() != null) {
                            knowledgeByDocument.put(document.getId(), document.getKnowledgeId());
                        }
                    }
                }
            }
            for (Chunk chunk : stored) {
                if (chunk == null || chunk.getId() == null) {
                    continue;
                }
                Long owner = knowledgeByDocument.get(chunk.getDocumentId());
                if (knowledgeId.equals(owner)) {
                    contents.put(chunk.getId(), chunk.getContent());
                }
            }
        } catch (Exception e) {
            log.warn("读取标准 Chunk 原文失败", e);
        }
        return contents;
    }

    private Map<Long, String> loadContents(List<RetrievalTraceChunkVO> chunks) {
        Map<Long, String> contents = new HashMap<>();
        if (chunkService == null) {
            return contents;
        }
        Set<Long> ids = new HashSet<>();
        for (RetrievalTraceChunkVO chunk : chunks) {
            if (chunk.getChunkId() != null) {
                ids.add(chunk.getChunkId());
            }
        }
        if (ids.isEmpty()) {
            return contents;
        }
        try {
            Collection<Chunk> stored = chunkService.listByIds(ids);
            if (stored == null) {
                return contents;
            }
            for (Chunk chunk : stored) {
                if (chunk != null && chunk.getId() != null) {
                    contents.put(chunk.getId(), chunk.getContent());
                }
            }
        } catch (Exception e) {
            log.warn("读取检索链路 chunk 正文失败", e);
        }
        return contents;
    }

    private String documentName(Map<Long, String> names, Long documentId) {
        if (documentId == null) {
            return null;
        }
        if (names.containsKey(documentId)) {
            return names.get(documentId);
        }
        String name = null;
        if (documentService != null) {
            try {
                name = documentService.getDocumentName(documentId);
            } catch (Exception e) {
                log.warn("读取文档名称失败, documentId={}", documentId, e);
            }
        }
        names.put(documentId, name);
        return name;
    }

    private static List<RetrievalTraceChunkVO> allChunks(RagEvalRetrievalTraceVO view) {
        List<RetrievalTraceChunkVO> chunks = new ArrayList<>();
        if (view.getRrfHits() != null) {
            chunks.addAll(view.getRrfHits());
        }
        return chunks;
    }
}
