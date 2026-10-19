package com.zx.consultant.rag.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zx.consultant.document.entity.Chunk;
import com.zx.consultant.document.service.ChunkService;
import com.zx.consultant.document.service.DocumentService;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.retriever.BM25Retriever;
import com.zx.consultant.rag.retriever.HybridRetriever;
import com.zx.consultant.rag.retriever.VectorRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 临时诊断入口：只跑 golden-set 中现有 BadCase，采集两路召回 / RRF / 过滤快照。
 * 不改检索阈值，不写评测历史。启动参数 {@code --diagnose-badcase}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BadCaseRecallDiagnosticRunner implements ApplicationRunner {

    private static final Set<Integer> BAD_CASE_IDS = Set.of(16, 29, 34, 37);
    private static final int EVAL_TOP_K = 5;
    private static final int EXTENDED_LIMIT = 50;
    private static final int CONTENT_PREVIEW = 500;
    private static final int EXPECTED_CHUNK_PREVIEW = 1500;

    private final ConfigurableApplicationContext context;
    private final RagEvalService ragEvalService;
    private final HybridRetriever hybridRetriever;
    private final VectorRetriever vectorRetriever;
    private final BM25Retriever bm25Retriever;
    private final DocumentService documentService;
    private final ChunkService chunkService;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.eval.knowledge-id:#{null}}")
    private Long knowledgeId;

    @Value("${app.rag.retriever.max-results:5}")
    private Integer retrieverMaxResults;

    @Value("${app.rag.retriever.min-score:0.75}")
    private Double minScore;

    @Value("${app.rag.retriever.bm25-min-score:1.0}")
    private Double bm25MinScore;

    @Value("${app.rag.retriever.rrf-k:60}")
    private Integer rrfK;

    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-v3}")
    private String embeddingModelName;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("diagnose-badcase")
                && !args.getNonOptionArgs().contains("diagnose-badcase")) {
            return;
        }
        try {
            Path out = diagnoseAndWrite();
            log.info("BadCase 召回诊断已写入: {}", out.toAbsolutePath());
            System.out.println("DIAGNOSIS_FILE=" + out.toAbsolutePath());
        } catch (Exception e) {
            log.error("BadCase 召回诊断失败", e);
            SpringApplication.exit(context, () -> 1);
            System.exit(1);
            return;
        }
        SpringApplication.exit(context, () -> 0);
        System.exit(0);
    }

    private Path diagnoseAndWrite() throws Exception {
        if (knowledgeId == null) {
            throw new IllegalStateException("请配置 app.rag.eval.knowledge-id");
        }
        EvalDataset dataset = ragEvalService.loadDataset();
        List<EvalCase> cases = dataset.getCases().stream()
                .filter(evalCase -> evalCase.getId() != null && BAD_CASE_IDS.contains(evalCase.getId()))
                .toList();
        if (cases.size() != BAD_CASE_IDS.size()) {
            throw new IllegalStateException("golden-set 中未找齐 BadCase " + BAD_CASE_IDS);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("datasetName", dataset.getName());
        report.put("knowledgeId", knowledgeId);
        report.put("embeddingModel", embeddingModelName);
        report.put("codePathNotes", Map.of(
                "evalUsesOriginalQuery", true,
                "vectorSetsMinScore", false,
                "vectorMinScoreLibraryDefault", 0,
                "hybridMinScoreAppliedIn", "HybridRetriever.shouldFilter after RRF merge",
                "evalTopK", EVAL_TOP_K,
                "retrieverMaxResults", retrieverMaxResults));
        report.put("config", Map.of(
                "retrieverMaxResults", retrieverMaxResults,
                "minScore", minScore,
                "bm25MinScore", bm25MinScore,
                "rrfK", rrfK,
                "bm25Index", bm25Retriever.getIndexName()));

        List<Map<String, Object>> caseReports = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            caseReports.add(diagnoseCase(evalCase));
        }
        report.put("cases", caseReports);

        ObjectMapper pretty = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        Path out = resolveOutputPath();
        Files.createDirectories(out.getParent());
        Files.writeString(out, pretty.writeValueAsString(report));
        return out;
    }

    private Map<String, Object> diagnoseCase(EvalCase evalCase) {
        String query = evalCase.getQuery();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", evalCase.getId());
        item.put("query", query);
        item.put("expectedDocIds", evalCase.getExpectedDocIds());
        item.put("expectedDocuments", describeExpectedDocs(evalCase.getExpectedDocIds()));
        item.put("bm25EscapedQuery", bm25Retriever.previewEscapedQuery(query));
        item.put("bm25RedisQuery", bm25Retriever.buildRedisQuery(
                knowledgeId, bm25Retriever.previewEscapedQuery(query)));

        HybridRetriever.RecallTrace trace = hybridRetriever.diagnose(query, knowledgeId);
        List<RetrievedChunk> after = hybridRetriever.retrieve(query, knowledgeId);
        List<RetrievedChunk> extendedVector = vectorRetriever.retrieve(query, knowledgeId, EXTENDED_LIMIT);
        List<RetrievedChunk> extendedBm25 = bm25Retriever.retrieve(query, knowledgeId, EXTENDED_LIMIT);

        List<String> diagnoseDocIds = ragEvalService.toRetrievedDocIds(trace.finalTopK, EVAL_TOP_K);
        List<String> afterDocIds = ragEvalService.toRetrievedDocIds(after, EVAL_TOP_K);
        item.put("finalRecallConsistent", sameChunkSequence(trace.finalTopK, after));
        item.put("evalTop5DocIdsConsistent", diagnoseDocIds.equals(afterDocIds));
        item.put("evalTop5DocIds", decorateDocIds(diagnoseDocIds));
        item.put("evalTop5AfterSecondRetrieve", decorateDocIds(afterDocIds));

        item.put("vectorHits", decorateChunks(trace.vectorHits, true));
        item.put("bm25Hits", decorateChunks(trace.bm25Hits, true));
        item.put("extendedVectorHits", decorateChunks(extendedVector, true));
        item.put("extendedBm25Hits", decorateChunks(extendedBm25, true));
        item.put("mergedByRrf", decorateChunks(trace.mergedByRrf, false));
        item.put("filtered", decorateFiltered(trace.filtered));
        item.put("afterFilter", decorateChunks(trace.afterFilter, false));
        item.put("finalTopK", decorateChunks(trace.finalTopK, false));
        item.put("expectedDocTrace", expectedDocTraces(
                evalCase.getExpectedDocIds(),
                trace,
                extendedVector,
                extendedBm25,
                diagnoseDocIds));
        return item;
    }

    private List<Map<String, Object>> describeExpectedDocs(List<String> expectedDocIds) {
        List<Map<String, Object>> docs = new ArrayList<>();
        if (expectedDocIds == null) {
            return docs;
        }
        for (String docId : expectedDocIds) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("documentId", docId);
            doc.put("fileName", docName(docId));
            Long id = parseLong(docId);
            if (id == null) {
                doc.put("chunks", List.of());
                docs.add(doc);
                continue;
            }
            List<Chunk> chunks = chunkService.list(new LambdaQueryWrapper<Chunk>()
                    .eq(Chunk::getDocumentId, id)
                    .orderByAsc(Chunk::getChunkIndex));
            List<Map<String, Object>> chunkViews = new ArrayList<>();
            for (Chunk chunk : chunks) {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("chunkId", chunk.getId());
                view.put("chunkIndex", chunk.getChunkIndex());
                view.put("page", chunk.getPage());
                view.put("tokenSize", chunk.getTokenSize());
                view.put("content", preview(chunk.getContent(), EXPECTED_CHUNK_PREVIEW));
                chunkViews.add(view);
            }
            doc.put("chunkCount", chunks.size());
            doc.put("chunks", chunkViews);
            docs.add(doc);
        }
        return docs;
    }

    private List<Map<String, Object>> expectedDocTraces(
            List<String> expectedDocIds,
            HybridRetriever.RecallTrace trace,
            List<RetrievedChunk> extendedVector,
            List<RetrievedChunk> extendedBm25,
            List<String> evalTop5) {
        List<Map<String, Object>> traces = new ArrayList<>();
        if (expectedDocIds == null) {
            return traces;
        }
        for (String docId : expectedDocIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("documentId", docId);
            row.put("fileName", docName(docId));
            row.put("inVectorTop8", docRank(trace.vectorHits, docId));
            row.put("inExtendedVectorTop50", docRank(extendedVector, docId));
            row.put("inBm25Top8", docRank(trace.bm25Hits, docId));
            row.put("inExtendedBm25Top50", docRank(extendedBm25, docId));
            row.put("inMergedRrf", docRank(trace.mergedByRrf, docId));
            row.put("filtered", firstFiltered(trace.filtered, docId));
            row.put("afterFilterRank", docRank(trace.afterFilter, docId));
            row.put("finalTopKRank", docRank(trace.finalTopK, docId));
            row.put("inEvalTop5", evalTop5.contains(docId) ? evalTop5.indexOf(docId) + 1 : null);
            traces.add(row);
        }
        return traces;
    }

    private Map<String, Object> firstFiltered(
            List<HybridRetriever.FilteredCandidate> filtered, String docId) {
        if (filtered == null) {
            return null;
        }
        for (HybridRetriever.FilteredCandidate item : filtered) {
            if (item == null || item.chunk == null) {
                continue;
            }
            if (Objects.equals(docId, String.valueOf(item.chunk.getDocumentId()))) {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("reason", item.reason);
                view.put("chunk", decorateChunk(item.chunk, false));
                return view;
            }
        }
        return null;
    }

    private Map<String, Object> docRank(List<RetrievedChunk> hits, String docId) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("hit", false);
        view.put("rank", null);
        view.put("chunkId", null);
        view.put("vectorScore", null);
        view.put("bm25Score", null);
        view.put("finalScore", null);
        if (hits == null) {
            return view;
        }
        for (int i = 0; i < hits.size(); i++) {
            RetrievedChunk chunk = hits.get(i);
            if (chunk == null || chunk.getDocumentId() == null) {
                continue;
            }
            if (docId.equals(String.valueOf(chunk.getDocumentId()))) {
                view.put("hit", true);
                view.put("rank", i + 1);
                view.put("chunkId", chunk.getChunkId());
                view.put("vectorScore", chunk.getVectorScore());
                view.put("bm25Score", chunk.getBm25Score());
                view.put("finalScore", chunk.getFinalScore());
                return view;
            }
        }
        return view;
    }

    private List<Map<String, Object>> decorateChunks(List<RetrievedChunk> chunks, boolean includeContent) {
        List<Map<String, Object>> views = new ArrayList<>();
        if (chunks == null) {
            return views;
        }
        int rank = 1;
        for (RetrievedChunk chunk : chunks) {
            Map<String, Object> view = decorateChunk(chunk, includeContent);
            view.put("rank", rank++);
            views.add(view);
        }
        return views;
    }

    private List<Map<String, Object>> decorateFiltered(List<HybridRetriever.FilteredCandidate> filtered) {
        List<Map<String, Object>> views = new ArrayList<>();
        if (filtered == null) {
            return views;
        }
        for (HybridRetriever.FilteredCandidate item : filtered) {
            Map<String, Object> view = decorateChunk(item.chunk, false);
            view.put("reason", item.reason);
            views.add(view);
        }
        return views;
    }

    private Map<String, Object> decorateChunk(RetrievedChunk chunk, boolean includeContent) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (chunk == null) {
            return view;
        }
        view.put("chunkId", chunk.getChunkId());
        view.put("documentId", chunk.getDocumentId() == null ? null : String.valueOf(chunk.getDocumentId()));
        view.put("fileName", chunk.getDocumentId() == null ? null : docName(String.valueOf(chunk.getDocumentId())));
        view.put("page", chunk.getPage());
        view.put("vectorScore", chunk.getVectorScore());
        view.put("bm25Score", chunk.getBm25Score());
        view.put("finalScore", chunk.getFinalScore());
        if (includeContent) {
            view.put("contentPreview", preview(chunk.getContent(), CONTENT_PREVIEW));
        }
        return view;
    }

    private List<Map<String, Object>> decorateDocIds(List<String> docIds) {
        List<Map<String, Object>> views = new ArrayList<>();
        if (docIds == null) {
            return views;
        }
        int rank = 1;
        for (String docId : docIds) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("rank", rank++);
            view.put("documentId", docId);
            view.put("fileName", docName(docId));
            views.add(view);
        }
        return views;
    }

    private boolean sameChunkSequence(List<RetrievedChunk> left, List<RetrievedChunk> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            RetrievedChunk a = left.get(i);
            RetrievedChunk b = right.get(i);
            if (!Objects.equals(a.getChunkId(), b.getChunkId())
                    || !Objects.equals(a.getDocumentId(), b.getDocumentId())
                    || !Objects.equals(a.getFinalScore(), b.getFinalScore())) {
                return false;
            }
        }
        return true;
    }

    private String docName(String documentId) {
        Long id = parseLong(documentId);
        if (id == null) {
            return documentId;
        }
        try {
            return documentService.getDocumentName(id);
        } catch (Exception e) {
            return documentId;
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String preview(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n");
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private Path resolveOutputPath() {
        String relative = "docs/eval/result/badcase-recall-diagnosis.json";
        Path direct = Path.of(relative);
        if (direct.getParent() != null && Files.isDirectory(direct.getParent())) {
            return direct;
        }
        Path fromModule = Path.of("..", "..", relative);
        if (fromModule.getParent() != null && Files.isDirectory(fromModule.getParent())) {
            return fromModule;
        }
        Path fromBackend = Path.of("..", relative);
        if (fromBackend.getParent() != null && Files.isDirectory(fromBackend.getParent())) {
            return fromBackend;
        }
        return direct;
    }
}
