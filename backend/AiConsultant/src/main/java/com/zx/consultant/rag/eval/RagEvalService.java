package com.zx.consultant.rag.eval;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.retriever.HybridRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG Evaluation Runner：读 golden-set → HybridRetriever → 文档级指标 → 打印 + 写报告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalService {

    private static final int TOP_K = 5;

    private final HybridRetriever hybridRetriever;
    private final ObjectMapper objectMapper;
    private final EvalReportWriter evalReportWriter;

    @Value("${app.rag.eval.knowledge-id:#{null}}")
    private Long knowledgeId;

    @Value("${app.rag.eval.golden-set-path:docs/eval/golden-set.json}")
    private String goldenSetPath;

    public EvalResult evaluate() {
        if (knowledgeId == null) {
            throw new IllegalStateException("请配置 app.rag.eval.knowledge-id（评测知识库 ID）");
        }

        File goldenFile = resolveGoldenSetFile();
        List<EvalCase> goldenSet = loadGoldenSet(goldenFile);
        if (goldenSet.isEmpty()) {
            throw new IllegalStateException("golden-set 为空: " + goldenFile.getAbsolutePath());
        }

        int hitCount = 0;
        double recallSum = 0.0;
        double mrrSum = 0.0;
        List<FailedCase> failedCases = new ArrayList<>();

        for (EvalCase evalCase : goldenSet) {
            String query = evalCase.getQuery();
            List<RetrievedChunk> chunks = hybridRetriever.retrieve(query, knowledgeId);
            List<String> retrievedDocIds = toRetrievedDocIds(chunks, TOP_K);

            boolean hit = isHit(retrievedDocIds, evalCase.getExpectedDocIds());
            if (hit) {
                hitCount++;
            } else {
                FailedCase failed = new FailedCase();
                failed.setId(evalCase.getId());
                failed.setQuery(query);
                failed.setExpectedDocIds(evalCase.getExpectedDocIds());
                failed.setRetrievedDocIds(retrievedDocIds);
                failed.setTopScore(topScore(chunks));
                failedCases.add(failed);
            }
            recallSum += hit ? 1.0 : 0.0;
            mrrSum += reciprocalRank(retrievedDocIds, evalCase.getExpectedDocIds());
        }

        int total = goldenSet.size();
        EvalResult result = new EvalResult();
        result.setTotal(total);
        result.setHitRate((double) hitCount / total);
        result.setRecall(recallSum / total);
        result.setMrr(mrrSum / total);
        result.setFailedCases(failedCases);

        printReport(goldenFile.getName(), result);
        evalReportWriter.write(result);
        return result;
    }

    private Double topScore(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        RetrievedChunk first = chunks.get(0);
        if (first.getFinalScore() != null) {
            return first.getFinalScore();
        }
        if (first.getVectorScore() != null) {
            return first.getVectorScore();
        }
        return first.getScore();
    }

    /**
     * chunk → documentId，保序去重，截断 TopK。
     */
    List<String> toRetrievedDocIds(List<RetrievedChunk> chunks, int topK) {
        LinkedHashSet<String> docIds = new LinkedHashSet<>();
        if (chunks == null) {
            return List.of();
        }
        for (RetrievedChunk chunk : chunks) {
            if (chunk.getDocumentId() == null) {
                continue;
            }
            docIds.add(String.valueOf(chunk.getDocumentId()));
            if (docIds.size() >= topK) {
                break;
            }
        }
        return new ArrayList<>(docIds);
    }

    /** TopK 中是否包含任意 expected 文档 */
    boolean isHit(List<String> retrievedDocIds, List<String> expectedDocIds) {
        if (retrievedDocIds == null || expectedDocIds == null || expectedDocIds.isEmpty()) {
            return false;
        }
        Set<String> expected = new HashSet<>(expectedDocIds);
        for (String docId : retrievedDocIds) {
            if (expected.contains(docId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * MRR：第一个命中 expected 的文档排名倒数；未命中为 0。
     */
    double reciprocalRank(List<String> retrievedDocIds, List<String> expectedDocIds) {
        if (retrievedDocIds == null || expectedDocIds == null || expectedDocIds.isEmpty()) {
            return 0.0;
        }
        Set<String> expected = new HashSet<>(expectedDocIds);
        for (int i = 0; i < retrievedDocIds.size(); i++) {
            if (expected.contains(retrievedDocIds.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private List<EvalCase> loadGoldenSet(File file) {
        try {
            return objectMapper.readValue(file, new TypeReference<List<EvalCase>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("读取 golden-set 失败: " + file.getAbsolutePath(), e);
        }
    }

    private File resolveGoldenSetFile() {
        File direct = new File(goldenSetPath);
        if (direct.isFile()) {
            return direct;
        }
        Path fromModule = Path.of("..", "..", goldenSetPath);
        File moduleRelative = fromModule.toFile();
        if (moduleRelative.isFile()) {
            return moduleRelative;
        }
        Path fromBackend = Path.of("..", goldenSetPath);
        File backendRelative = fromBackend.toFile();
        if (backendRelative.isFile()) {
            return backendRelative;
        }
        throw new IllegalStateException(
                "找不到 golden-set: " + goldenSetPath + "（cwd=" + new File(".").getAbsolutePath() + "）");
    }

    private void printReport(String datasetName, EvalResult result) {
        String report = """
                
                ==========RAG Evaluation==========
                Dataset:    %s
                Total Case: %d
                Hit Rate:   %s
                Recall@5:   %s
                MRR:        %.2f
                Failed:     %d
                ==================================
                """.formatted(
                datasetName,
                result.getTotal(),
                formatPercent(result.getHitRate()),
                formatPercent(result.getRecall()),
                result.getMrr(),
                result.getFailedCases() == null ? 0 : result.getFailedCases().size());
        System.out.print(report);
    }

    private String formatPercent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }
}
