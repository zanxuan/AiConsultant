package com.zx.consultant.rag.eval;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * 供 HTTP 入口写入评测知识库 ID，对应配置 app.rag.eval.knowledge-id。
     * 不改变 evaluate() 的计算逻辑。
     */
    public void setKnowledgeId(Long knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    /**
     * 只读加载当前 golden-set，不执行检索或指标计算。
     */
    public EvalDataset loadDataset() {
        File goldenFile = resolveGoldenSetFile();
        List<EvalCase> cases = loadGoldenSet(goldenFile);
        EvalDataset dataset = new EvalDataset();
        dataset.setName(goldenFile.getName());
        dataset.setCases(cases);
        return dataset;
    }

    /**
     * 评估
     * @return 评估结果
     */
    public EvalResult evaluate() {
        if (knowledgeId == null) {
            throw new IllegalStateException("请配置 app.rag.eval.knowledge-id（评测知识库 ID）");
        }

        // 解析黄金集文件
        File goldenFile = resolveGoldenSetFile();
        List<EvalCase> goldenSet = loadGoldenSet(goldenFile);
        if (goldenSet.isEmpty()) {
            throw new IllegalStateException("golden-set 为空: " + goldenFile.getAbsolutePath());
        }

        int hitCount = 0;
        double recallSum = 0.0;
        double mrrSum = 0.0;
        List<FailedCase> failedCases = new ArrayList<>();
        List<EvalCaseResult> caseResults = new ArrayList<>();
        List<Long> retrieveLatenciesMs = new ArrayList<>();

        // 遍历黄金集
        for (EvalCase evalCase : goldenSet) {
            String query = evalCase.getQuery();
            long retrieveStartNs = System.nanoTime();
            List<RetrievedChunk> chunks = hybridRetriever.retrieve(query, knowledgeId);
            long latencyMs = (System.nanoTime() - retrieveStartNs) / 1_000_000L;
            retrieveLatenciesMs.add(latencyMs);
            List<String> retrievedDocIds = toRetrievedDocIds(chunks, TOP_K);

            boolean hit = isHit(retrievedDocIds, evalCase.getExpectedDocIds());
            double caseRecall = hit ? 1.0 : 0.0;
            double caseMrr = reciprocalRank(retrievedDocIds, evalCase.getExpectedDocIds());
            if (hit) {
                hitCount++;
            } else {
                FailedCase failed = new FailedCase();
                failed.setId(evalCase.getId());
                failed.setQuery(query);
                failed.setExpectedDocIds(evalCase.getExpectedDocIds());
                failed.setRetrievedDocIds(retrievedDocIds);
                failed.setTopScore(topScore(chunks));
                failed.setLatencyMs(latencyMs);
                failedCases.add(failed);
            }
            recallSum += caseRecall;
            mrrSum += caseMrr;

            EvalCaseResult caseResult = new EvalCaseResult();
            caseResult.setId(evalCase.getId());
            caseResult.setQuery(query);
            caseResult.setExpectedDocIds(evalCase.getExpectedDocIds());
            caseResult.setRetrievedDocIds(retrievedDocIds);
            caseResult.setTopScore(topScore(chunks));
            caseResult.setLatencyMs(latencyMs);
            caseResult.setHit(hit);
            caseResult.setRecall(caseRecall);
            caseResult.setMrr(caseMrr);
            caseResults.add(caseResult);
        }

        // 计算评估结果
        int total = goldenSet.size();
        EvalResult result = new EvalResult();
        result.setTotal(total);
        result.setHitRate((double) hitCount / total);
        result.setRecall(recallSum / total);
        result.setMrr(mrrSum / total);
        result.setFailedCases(failedCases);
        result.setCases(caseResults);
        result.setAvgLatencyMs(averageLatencyMs(retrieveLatenciesMs));
        result.setP95LatencyMs(p95LatencyMs(retrieveLatenciesMs));

        // 打印报告
        printReport(goldenFile.getName(), result);
        // 写报告
        evalReportWriter.write(result);
        return result;
    }

    /**
     * 获取最高得分
     * @param chunks 检索到的片段
     * @return 最高得分
     */
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

    /**
     * 加载黄金集
     * @param file 文件
     * @return 黄金集
     */
    private List<EvalCase> loadGoldenSet(File file) {
        try {
            return objectMapper.readValue(file, new TypeReference<List<EvalCase>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("读取 golden-set 失败: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * 解析黄金集文件
     * @return 黄金集文件
     */
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

    /**
     * 打印报告
     * @param datasetName 数据集名称
     * @param result 评估结果
     */
    private void printReport(String datasetName, EvalResult result) {
        String report = """
                
                ==========RAG Evaluation==========
                Dataset:    %s
                Total Case: %d
                Hit Rate:   %s
                Recall@5:   %s
                MRR:        %.2f
                Avg Latency:%s
                P95 Latency:%s
                Failed:     %d
                ==================================
                """.formatted(
                datasetName,
                result.getTotal(),
                formatPercent(result.getHitRate()),
                formatPercent(result.getRecall()),
                result.getMrr(),
                formatLatencyMs(result.getAvgLatencyMs()),
                formatLatencyMs(result.getP95LatencyMs()),
                result.getFailedCases() == null ? 0 : result.getFailedCases().size());
        System.out.print(report);
    }

    /**
     * 格式化百分比
     * @param ratio 比例
     * @return 百分比
     */
    private String formatPercent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    private static String formatLatencyMs(Long latencyMs) {
        if (latencyMs == null) {
            return "N/A";
        }
        return latencyMs + " ms";
    }

    /**
     * 检索耗时算术平均，四舍五入为毫秒；空列表返回 null，避免除零。
     */
    Long averageLatencyMs(List<Long> latenciesMs) {
        if (latenciesMs == null || latenciesMs.isEmpty()) {
            return null;
        }
        long sum = 0L;
        for (Long latency : latenciesMs) {
            sum += latency == null ? 0L : latency;
        }
        return Math.round(sum / (double) latenciesMs.size());
    }

    /**
     * P95：升序后取 ceil(0.95 * n) 名（1-based），再转 0-based 下标。
     * 空列表返回 null；下标夹在 [0, n-1]，避免越界。
     */
    Long p95LatencyMs(List<Long> latenciesMs) {
        if (latenciesMs == null || latenciesMs.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(latenciesMs.size());
        for (Long latency : latenciesMs) {
            sorted.add(latency == null ? 0L : latency);
        }
        Collections.sort(sorted);
        int n = sorted.size();
        int index = (int) Math.ceil(0.95d * n) - 1;
        if (index < 0) {
            index = 0;
        } else if (index >= n) {
            index = n - 1;
        }
        return sorted.get(index);
    }
}
