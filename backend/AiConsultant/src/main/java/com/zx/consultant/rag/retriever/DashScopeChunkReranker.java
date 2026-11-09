package com.zx.consultant.rag.retriever;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zx.consultant.rag.entity.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;

/**
 * 调用通义 gte-rerank，只重排 Hybrid 已经截好的候选。
 * 请求失败、超时或返回条数对不上时不抛给检索链路，由调用方退回 RRF 顺序。
 */
@Slf4j
@Component
public class DashScopeChunkReranker implements ChunkReranker {

    private static final int REASON_LIMIT = 200;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${app.rag.retriever.rerank.model:gte-rerank-v2}")
    private String model;

    @Value("${app.rag.retriever.rerank.url:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}")
    private String url;

    @Value("${app.rag.retriever.rerank.timeout-ms:8000}")
    private Integer timeoutMs;

    public DashScopeChunkReranker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public RerankScores score(String query, List<RetrievedChunk> chunks) {
        long startNs = System.nanoTime();
        if (apiKey == null || apiKey.isBlank()) {
            return failed(startNs, "missing api key");
        }
        if (model == null || model.isBlank() || url == null || url.isBlank()) {
            return failed(startNs, "rerank model or url is blank");
        }
        if (chunks == null || chunks.isEmpty()) {
            return failed(startNs, "no chunks to rerank");
        }

        try {
            String body = objectMapper.writeValueAsString(requestBody(query, chunks));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs == null || timeoutMs < 1 ? 8000 : timeoutMs))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failed(startNs, "http " + response.statusCode() + " " + snippet(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            double[] scores = toScores(root, chunks.size());
            if (scores == null) {
                String reason = errorMessage(root);
                return failed(startNs, reason == null ? "rerank response incomplete" : reason);
            }
            return new RerankScores(scores, elapsedMs(startNs), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(startNs, "interrupted");
        } catch (Exception e) {
            return failed(startNs, e.getClass().getSimpleName() + ": " + snippet(e.getMessage()));
        }
    }

    /** 与官方 text-rerank 请求一致：documents 用切块正文，top_n 取全部候选，只要分数不要回传正文 */
    private ObjectNode requestBody(String query, List<RetrievedChunk> chunks) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ObjectNode input = body.putObject("input");
        input.put("query", query == null ? "" : query);
        ArrayNode documents = input.putArray("documents");
        for (RetrievedChunk chunk : chunks) {
            String content = chunk == null ? null : chunk.getContent();
            documents.add(content == null ? "" : content);
        }
        ObjectNode parameters = body.putObject("parameters");
        parameters.put("return_documents", false);
        parameters.put("top_n", chunks.size());
        return body;
    }

    /**
     * 把返回结果还原成入参下标上的分数。
     * 条数、下标必须正好覆盖每个候选一次，否则整批作废，避免丢掉或重复切块。
     */
    static double[] toScores(JsonNode root, int size) {
        if (root == null || size < 1) {
            return null;
        }
        JsonNode results = root.path("output").path("results");
        if (!results.isArray() || results.size() != size) {
            return null;
        }
        double[] scores = new double[size];
        boolean[] seen = new boolean[size];
        for (JsonNode item : results) {
            JsonNode indexNode = item.get("index");
            JsonNode scoreNode = item.get("relevance_score");
            if (indexNode == null || !indexNode.isIntegralNumber() || scoreNode == null || !scoreNode.isNumber()) {
                return null;
            }
            int index = indexNode.asInt();
            double score = scoreNode.asDouble();
            if (index < 0 || index >= size || seen[index] || !Double.isFinite(score)) {
                return null;
            }
            seen[index] = true;
            scores[index] = score;
        }
        return scores;
    }

    /** 通义失败时 body 里带 code / message。没有这两项时返回 null，交给调用方写默认原因 */
    static String errorMessage(JsonNode root) {
        if (root == null) {
            return null;
        }
        String code = root.path("code").asText("");
        String message = root.path("message").asText("");
        if (code.isEmpty() && message.isEmpty()) {
            return null;
        }
        String reason = code.isEmpty() ? message : code + ": " + message;
        return snippet(reason);
    }

    private RerankScores failed(long startNs, String reason) {
        return new RerankScores(null, elapsedMs(startNs), snippet(reason));
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() <= REASON_LIMIT) {
            return oneLine;
        }
        return oneLine.substring(0, REASON_LIMIT);
    }
}
