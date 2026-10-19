package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.document.entity.Chunk;
import com.zx.consultant.document.service.ChunkService;
import com.zx.consultant.document.service.DocumentService;
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import com.zx.consultant.rag.eval.mapper.RagEvalRetrievalTraceMapper;
import com.zx.consultant.rag.eval.vo.RagEvalRunDetailVO;
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
