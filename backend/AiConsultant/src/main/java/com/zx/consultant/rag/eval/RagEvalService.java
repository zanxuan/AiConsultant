package com.zx.consultant.rag.eval;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.knowledge.entity.KnowledgeBase;
import com.zx.consultant.knowledge.mapper.KnowledgeBaseMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import com.zx.consultant.rag.eval.vo.EvalRunExecuteVO;
import com.zx.consultant.rag.retriever.HybridRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG Evaluation：评测核心只消费用例列表和知识库 ID。
 * 固定 golden-set 与上传测试集都先解析成 {@link EvalCase}，再进入同一套指标计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalService {

    private static final int TOP_K = 5;

    private final HybridRetriever hybridRetriever;
    private final ObjectMapper objectMapper;
    private final EvalReportWriter evalReportWriter;
    private final RagEvalRunService ragEvalRunService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RagEvalRetrievalTraceService retrievalTraceService;

    /** 检索评测实际使用的向量模型，不参与指标计算 */
    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-v3}")
    private String embeddingModelName;

    @Value("${app.rag.eval.knowledge-id:#{null}}")
    private Long knowledgeId;

    @Value("${app.rag.eval.golden-set-path:docs/eval/golden-set.json}")
    private String goldenSetPath;

    /** 只写入配置快照，不参与指标计算 */
    @Value("${app.rag.retriever.max-results:5}")
    private Integer retrieverMaxResults;

    @Value("${app.rag.retriever.min-score:0.75}")
    private Double retrieverMinScore;

    /** 只写入配置快照，与 HybridRetriever 的 BM25 阈值一致 */
    @Value("${app.rag.retriever.bm25-min-score:1.0}")
    private Double retrieverBm25MinScore;

    @Value("${app.rag.retriever.rrf-k:60}")
    private Integer rrfK;

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
     * 旧入口：读取固定 golden-set，再交给评测核心。
     * 供 {@code --eval}、{@link RagEvalRunner} 和未上传文件的 HTTP 调用继续使用。
     */
    public EvalResult evaluate() {
        return evaluateGoldenSet(knowledgeId);
    }

    /**
     * 旧入口的显式知识库参数。传入 null 时回退到配置项 app.rag.eval.knowledge-id。
     */
    public EvalResult evaluateGoldenSet(Long requestedKnowledgeId) {
        return executeGoldenSet(requestedKnowledgeId).getResult();
    }

    /**
     * 与 {@link #evaluateGoldenSet(Long)} 相同，额外带回 runId 供详情页跳转。
     */
    public EvalRunExecuteVO executeGoldenSet(Long requestedKnowledgeId) {
        Long id = requestedKnowledgeId != null ? requestedKnowledgeId : this.knowledgeId;
        if (id == null) {
            throw new IllegalStateException("请配置 app.rag.eval.knowledge-id（评测知识库 ID）");
        }
        File goldenFile = resolveGoldenSetFile();
        List<EvalCase> goldenSet = loadGoldenSet(goldenFile);
        if (goldenSet.isEmpty()) {
            throw new IllegalStateException("golden-set 为空: " + goldenFile.getAbsolutePath());
        }
        return execute(goldenSet, id, goldenFile.getName(), RagEvalDatasetSource.GOLDEN_SET);
    }

    /**
     * 评测核心：只根据用例和知识库 ID 计算 Hit@5 / Recall@5 / MRR，不读取文件、不解析上传。
     */
    public EvalResult evaluate(List<EvalCase> cases, Long knowledgeId) {
        return evaluate(cases, knowledgeId, "uploaded-dataset");
    }

    /**
     * datasetName 只用于控制台报告标题，不参与指标计算。上传测试集走这里，来源记为 UPLOAD。
     */
    public EvalResult evaluate(List<EvalCase> cases, Long knowledgeId, String datasetName) {
        return execute(cases, knowledgeId, datasetName).getResult();
    }

    /**
     * 上传测试集的执行入口。指标计算与 {@link #evaluate(List, Long, String)} 相同，并返回 runId。
     */
    public EvalRunExecuteVO execute(List<EvalCase> cases, Long knowledgeId, String datasetName) {
        return execute(cases, knowledgeId, datasetName, RagEvalDatasetSource.UPLOAD);
    }

    /**
     * 校验通过后创建 RUNNING 记录；正常结束更新成功，异常更新失败后继续抛出。
     * 历史写入失败只记日志，不改变指标、报告和原异常。
     */
    public EvalResult evaluate(List<EvalCase> cases, Long knowledgeId, String datasetName,
            RagEvalDatasetSource datasetSource) {
                
        return execute(cases, knowledgeId, datasetName, datasetSource).getResult();
    }

    /**
     * 评测计算与落库。runId 不写入 {@link EvalResult}，避免进结果快照。
     */
    public EvalRunExecuteVO execute(List<EvalCase> cases, Long knowledgeId, String datasetName,
            RagEvalDatasetSource datasetSource) {
        if (knowledgeId == null) {
            throw new IllegalStateException("请配置 app.rag.eval.knowledge-id（评测知识库 ID）");
        }
        if (cases == null || cases.isEmpty()) {
            throw new IllegalStateException("评测测试集为空");
        }

        String modelName = embeddingModelName;
        String knowledgeName = resolveKnowledgeName(knowledgeId);
        Long runId = createRunQuietly(cases, knowledgeId, datasetName, datasetSource, modelName, knowledgeName);
        long startNs = System.nanoTime();
        EvalResult result = null;
        try {
            CalculatedEval calculated = calculate(cases, knowledgeId);
            result = calculated.result;
            printReport(datasetName, result);
            evalReportWriter.write(result);
            // 报告写完再落检索明细。运行中途失败时不保存这批尚未提交的快照。
            saveTracesQuietly(runId, calculated.traces);
            markSuccessQuietly(runId, result, startNs, modelName, knowledgeName);

            EvalRunExecuteVO executed = new EvalRunExecuteVO();
            executed.setRunId(runId);
            executed.setResult(result);
            return executed;
        } catch (RuntimeException e) {
            markFailedQuietly(runId, result, startNs, e, modelName, knowledgeName);
            throw e;
        }
    }

    /**
     * 原有指标计算。每个用例只走一次 {@link HybridRetriever#diagnose}，指标使用同一次 finalTopK。
     * 报告仍由调用方在结果赋值后写出，写报告失败时结果可以记入失败记录。
     */
    private CalculatedEval calculate(List<EvalCase> cases, Long knowledgeId) {
        int hitCount = 0;
        double recallSum = 0.0;
        double mrrSum = 0.0;
        List<FailedCase> failedCases = new ArrayList<>();
        List<EvalCaseResult> caseResults = new ArrayList<>();
        List<Long> retrieveLatenciesMs = new ArrayList<>();
        List<RagEvalRetrievalTrace> traces = new ArrayList<>();

        for (EvalCase evalCase : cases) {
            String query = evalCase.getQuery();
            long retrieveStartNs = System.nanoTime();
            HybridRetriever.RecallTrace trace = hybridRetriever.diagnose(query, knowledgeId);
            long latencyMs = (System.nanoTime() - retrieveStartNs) / 1_000_000L;
            retrieveLatenciesMs.add(latencyMs);
            List<RetrievedChunk> chunks = trace.finalTopK == null ? List.of() : trace.finalTopK;
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
            // 成功和失败用例共用这一份快照，不额外再检索一次
            RetrievalTraceSnapshots.fillSummary(caseResult, trace, evalCase.getExpectedDocIds(), retrievedDocIds);
            if (evalCase.getId() != null) {
                traces.addAll(RetrievalTraceSnapshots.toRows(null, evalCase.getId(), trace, retrievedDocIds));
            }
            caseResults.add(caseResult);
        }

        int total = cases.size();
        EvalResult result = new EvalResult();
        result.setTotal(total);
        result.setHitRate((double) hitCount / total);
        result.setRecall(recallSum / total);
        result.setMrr(mrrSum / total);
        result.setFailedCases(failedCases);
        result.setCases(caseResults);
        result.setAvgLatencyMs(averageLatencyMs(retrieveLatenciesMs));
        result.setP95LatencyMs(p95LatencyMs(retrieveLatenciesMs));
        return new CalculatedEval(result, traces);
    }

    /**
     * 检索明细写入失败只记日志，不改变已算出的指标，也不把运行标成失败。
     */
    private void saveTracesQuietly(Long runId, List<RagEvalRetrievalTrace> traces) {
        if (runId == null || retrievalTraceService == null) {
            return;
        }
        try {
            retrievalTraceService.saveRunTraces(runId, traces);
        } catch (Exception e) {
            log.error("保存检索链路失败, runId={}", runId, e);
        }
    }

    /** 指标结果和待落库的检索明细。明细不进入 result_snapshot。 */
    private static final class CalculatedEval {
        private final EvalResult result;
        private final List<RagEvalRetrievalTrace> traces;

        private CalculatedEval(EvalResult result, List<RagEvalRetrievalTrace> traces) {
            this.result = result;
            this.traces = traces;
        }
    }

    /**
     * 历史库不可用时继续评测，避免影响原有接口。
     */
    private Long createRunQuietly(List<EvalCase> cases, Long knowledgeId, String datasetName,
            RagEvalDatasetSource datasetSource, String modelName, String knowledgeName) {
        try {
            return ragEvalRunService.createRunning(
                    BaseContext.getCurrentId(),
                    knowledgeId,
                    modelName,
                    knowledgeName,
                    datasetSource,
                    datasetName,
                    cases,
                    configSnapshot(datasetSource));
        } catch (Exception e) {
            log.error("创建评测记录失败，继续执行评测", e);
            return null;
        }
    }

    private void markSuccessQuietly(Long runId, EvalResult result, long startNs, String modelName,
            String knowledgeName) {
        if (runId == null) {
            return;
        }
        try {
            ragEvalRunService.markSuccess(runId, result, elapsedMs(startNs), modelName, knowledgeName);
        } catch (Exception e) {
            log.error("更新评测成功状态失败, runId={}", runId, e);
        }
    }

    private void markFailedQuietly(Long runId, EvalResult result, long startNs, Throwable error,
            String modelName, String knowledgeName) {
        if (runId == null) {
            return;
        }
        try {
            ragEvalRunService.markFailed(runId, result, elapsedMs(startNs), error, modelName, knowledgeName);
        } catch (Exception e) {
            log.error("更新评测失败状态失败, runId={}", runId, e);
        }
    }

    /**
     * 记下评测当时的知识库名称。查不到或查询失败时留空，不中断评测。
     */
    private String resolveKnowledgeName(Long knowledgeId) {
        if (knowledgeBaseMapper == null || knowledgeId == null) {
            return null;
        }
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeId);
            if (knowledgeBase == null || knowledgeBase.getName() == null || knowledgeBase.getName().isBlank()) {
                return null;
            }
            return knowledgeBase.getName();
        } catch (Exception e) {
            log.warn("读取知识库名称失败, knowledgeId={}", knowledgeId, e);
            return null;
        }
    }

    /**
     * 记录当时影响检索和打分的配置。固定集才写入 golden-set 路径。
     */
    private Map<String, Object> configSnapshot(RagEvalDatasetSource datasetSource) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("evalTopK", TOP_K);
        config.put("retrieverMaxResults", retrieverMaxResults);
        config.put("retrieverMinScore", retrieverMinScore);
        config.put("retrieverBm25MinScore", retrieverBm25MinScore);
        config.put("rrfK", rrfK);
        config.put("goldenSetPath", datasetSource == RagEvalDatasetSource.GOLDEN_SET ? goldenSetPath : null);
        return config;
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    /**
     * 与 golden-set 使用同一套 Jackson 解析，把 JSON 数组反序列化为 EvalCase。
     */
    public List<EvalCase> parseCases(InputStream input, String sourceName) {
        try {
            List<EvalCase> cases = objectMapper.readValue(input, new TypeReference<List<EvalCase>>() {});
            return cases == null ? List.of() : cases;
        } catch (Exception e) {
            throw new IllegalStateException("读取测试集失败: " + sourceName, e);
        }
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
        try (InputStream in = new FileInputStream(file)) {
            return parseCases(in, file.getAbsolutePath());
        } catch (IllegalStateException e) {
            throw e;
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
