package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 新旧测试集都通过 {@link RagEvalService#parseCases} 解析，并直接读取 docs/eval 下的真实文件。
 */
class EvalCaseParseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RagEvalService ragEvalService =
            new RagEvalService(null, objectMapper, null, null, null, null);

    @Test
    void goldenSetKeepsQueryAndExpectedDocIds() throws Exception {
        JsonNode root = readTree("golden-set.json");
        List<EvalCase> cases = parseFile("golden-set.json");

        assertEquals(root.size(), cases.size());
        assertTrue(cases.size() > 0);
        for (int i = 0; i < cases.size(); i++) {
            JsonNode raw = root.get(i);
            EvalCase evalCase = cases.get(i);
            assertEquals(raw.get("id").intValue(), evalCase.getId());
            assertEquals(raw.get("query").asText(), evalCase.getQuery());
            assertEquals(textValues(raw.get("expectedDocIds")), evalCase.getExpectedDocIds());
            assertNull(evalCase.getSourceCaseId());
            assertNull(evalCase.getGroundTruthChunkIds());
        }

        String serialized = objectMapper.writeValueAsString(cases.get(0));
        assertFalse(serialized.contains("sourceCaseId"));
        assertFalse(serialized.contains("groundTruthChunkIds"));
        assertTrue(serialized.contains("expectedDocIds"));
    }

    @Test
    void newRetrievalSetsKeepQuestionChunkIdsAndSourceCaseId() throws Exception {
        assertNewSet("qyh-hub-retrieval-set.json", 38);
        assertNewSet("qyh-hub-ops-manual-retrieval-set.json", 44);
        assertNewSet("qyh-hub-troubleshooting-guide-retrieval-set.json", 44);
    }

    @Test
    void queryWinsWhenQuestionIsAlsoPresent() {
        List<EvalCase> cases = parseJson("""
                [{
                  "id": 7,
                  "query": "旧问题",
                  "question": "新问题",
                  "expectedDocIds": ["100"],
                  "groundTruthChunkIds": ["200"]
                }]
                """);

        EvalCase evalCase = cases.get(0);
        assertEquals(7, evalCase.getId());
        assertNull(evalCase.getSourceCaseId());
        assertEquals("旧问题", evalCase.getQuery());
        assertEquals(List.of("100"), evalCase.getExpectedDocIds());
        assertEquals(List.of("200"), evalCase.getGroundTruthChunkIds());
    }

    @Test
    void numericIdRoundTripKeepsExplicitSourceCaseIdAndChunkText() throws Exception {
        String chunkId = "2113438807872655361";
        List<EvalCase> cases = parseJson("""
                [{
                  "id": 1,
                  "sourceCaseId": "test-001",
                  "query": "回读问题",
                  "groundTruthChunkIds": ["%s"]
                }]
                """.formatted(chunkId));

        EvalCase evalCase = cases.get(0);
        assertEquals(1, evalCase.getId());
        assertEquals("test-001", evalCase.getSourceCaseId());
        assertEquals("回读问题", evalCase.getQuery());
        assertNull(evalCase.getExpectedDocIds());
        assertEquals(List.of(chunkId), evalCase.getGroundTruthChunkIds());

        EvalCase again = objectMapper.readValue(objectMapper.writeValueAsString(evalCase), EvalCase.class);
        assertEquals(evalCase, again);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad-id", "TEST-001", "test-", "test-001x", "test- 1", "1"})
    void illegalCaseIdIsRejected(String rawId) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parseJson("[{\"id\":\"" + rawId + "\",\"question\":\"q\"}]"));

        assertTrue(error.getMessage().contains("用例 ID 格式非法"));
        assertTrue(error.getMessage().contains(rawId));
    }

    @Test
    void caseIdOutsideIntegerRangeIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parseJson("[{\"id\":\"test-2147483648\",\"question\":\"q\"}]"));

        assertTrue(error.getMessage().contains("超出 Integer 范围"));
        assertTrue(error.getMessage().contains("test-2147483648"));
    }

    @Test
    void numericChunkIdIsRejectedInsteadOfConverted() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> parseJson("""
                [{"id":"test-001","question":"q","groundTruthChunkIds":[2113438807872655361]}]
                """));

        assertTrue(error.getMessage().contains("groundTruthChunkIds[0]"));
        assertTrue(error.getMessage().contains("必须是字符串"));
    }

    @Test
    void conflictingSourceCaseIdIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> parseJson("""
                [{"id":"test-001","sourceCaseId":"test-002","question":"q"}]
                """));

        assertTrue(error.getMessage().contains("sourceCaseId 不一致"));
        assertTrue(error.getMessage().contains("test-001"));
    }

    private void assertNewSet(String fileName, int expectedSize) throws Exception {
        JsonNode root = readTree(fileName);
        List<EvalCase> cases = parseFile(fileName);

        assertEquals(expectedSize, cases.size());
        assertEquals(root.size(), cases.size());
        for (int i = 0; i < cases.size(); i++) {
            JsonNode raw = root.get(i);
            EvalCase evalCase = cases.get(i);
            String sourceCaseId = raw.get("id").asText();
            assertTrue(raw.get("id").isTextual());
            assertEquals(sourceCaseId, evalCase.getSourceCaseId());
            assertEquals(Integer.valueOf(sourceCaseId.substring("test-".length())), evalCase.getId());
            assertEquals(raw.get("question").asText(), evalCase.getQuery());
            assertFalse(evalCase.getQuery().isBlank());
            assertNull(evalCase.getExpectedDocIds());
            assertChunkIds(raw.get("groundTruthChunkIds"), evalCase.getGroundTruthChunkIds());
        }

        EvalCase first = cases.get(0);
        assertEquals(1, first.getId());
        assertEquals("test-001", first.getSourceCaseId());
        EvalCase reread = objectMapper.readValue(objectMapper.writeValueAsString(first), EvalCase.class);
        assertEquals(first, reread);
    }

    private static List<String> textValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
    }

    private static void assertChunkIds(JsonNode rawIds, List<String> actual) {
        assertEquals(rawIds.size(), actual.size());
        for (int i = 0; i < rawIds.size(); i++) {
            assertTrue(rawIds.get(i).isTextual(), "标准 chunk ID 在文件中应为字符串");
            String expected = rawIds.get(i).asText();
            assertEquals(expected, actual.get(i));
            assertTrue(expected.matches("\\d+"));
        }
    }

    private List<EvalCase> parseFile(String fileName) throws Exception {
        try (InputStream input = Files.newInputStream(evalFile(fileName))) {
            return ragEvalService.parseCases(input, fileName);
        }
    }

    private JsonNode readTree(String fileName) throws Exception {
        return objectMapper.readTree(evalFile(fileName).toFile());
    }

    private List<EvalCase> parseJson(String json) {
        return ragEvalService.parseCases(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                "inline.json");
    }

    private static Path evalFile(String fileName) {
        List<Path> candidates = List.of(
                Path.of("docs", "eval", fileName),
                Path.of("..", "docs", "eval", fileName),
                Path.of("..", "..", "docs", "eval", fileName));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到测试集: " + fileName + "，cwd=" + Path.of("").toAbsolutePath());
    }
}
