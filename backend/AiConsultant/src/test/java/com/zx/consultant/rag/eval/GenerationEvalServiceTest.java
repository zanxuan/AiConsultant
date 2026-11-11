package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.service.impl.PromptServiceImpl;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

/**
 * 单题生成评测不连接模型。客户端用桩，确认开关、cite 清洗和两项裁判是分开的。
 */
class GenerationEvalServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disabledSwitchDoesNotCallModel() {
        RecordingClient client = new RecordingClient();
        GenerationEvalService service = service(false, client);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.evaluate("内部代号是什么？", List.of(chunk("QYH-6")), "内部代号为 QYH-6。"));

        assertTrue(error.getMessage().contains("generation-enabled"));
        assertTrue(client.calls.isEmpty());
        assertFalse(service.generationEnabled());
    }

    @Test
    void oneQuestionGeneratesAndScoresBothMetrics() {
        RecordingClient client = new RecordingClient();
        GenerationEvalService service = service(true, client);

        GenerationEvalResult result = service.evaluate(
                "青屿空港货运枢纽的内部代号是什么？",
                List.of(chunk("青屿空港货运枢纽的内部代号为 QYH-6。")),
                "内部代号为 QYH-6。");

        assertEquals("内部代号是 QYH-6。", result.getAnswer());
        assertEquals(0.85d, result.getFaithfulness());
        assertEquals("答案准确引用了资料中的核心事实，但遗漏了一个条件。", result.getFaithfulnessReason());
        assertEquals(1.0d, result.getCorrectness());
        assertEquals("与标准答案一致。", result.getCorrectnessReason());
        assertEquals("qwen-plus", result.getGenerationModel());
        assertEquals("qwen3.8-flash", result.getJudgeModel());

        assertEquals(3, client.calls.size());
        assertEquals("generate", client.calls.get(0).kind);
        String generationText = userText(client.calls.get(0).messages);
        assertTrue(generationText.contains("青屿空港货运枢纽的内部代号为 QYH-6。"));
        assertTrue(generationText.contains("青屿空港货运枢纽的内部代号是什么？"));
        assertFalse(generationText.contains("【标准答案】"));

        assertEquals("judge", client.calls.get(1).kind);
        String faithfulnessText = userText(client.calls.get(1).messages);
        assertTrue(faithfulnessText.contains("【参考资料】"));
        assertTrue(faithfulnessText.contains("内部代号是 QYH-6。"));
        assertFalse(faithfulnessText.contains("【标准答案】"));

        assertEquals("judge", client.calls.get(2).kind);
        String correctnessText = userText(client.calls.get(2).messages);
        assertTrue(correctnessText.contains("【标准答案】"));
        assertTrue(correctnessText.contains("内部代号为 QYH-6。"));
        assertFalse(correctnessText.contains("青屿空港货运枢纽的内部代号为 QYH-6。"));
    }

    @Test
    void emptyRetrievalDoesNotCallModel() {
        RecordingClient client = new RecordingClient();
        GenerationEvalResult result = service(true, client)
                .evaluate("内部代号是什么？", List.of(), "内部代号为 QYH-6。");

        assertEquals(GenerationEvalService.EMPTY_RETRIEVAL_ANSWER, result.getAnswer());
        assertNull(result.getFaithfulness());
        assertEquals(GenerationEvalService.EMPTY_RETRIEVAL_REASON, result.getFaithfulnessReason());
        assertEquals(0.0d, result.getCorrectness());
        assertEquals(GenerationEvalService.EMPTY_RETRIEVAL_REASON, result.getCorrectnessReason());
        assertTrue(client.calls.isEmpty());
    }

    @Test
    void missingReferenceSkipsCorrectnessJudge() {
        RecordingClient client = new RecordingClient();
        GenerationEvalResult result = service(true, client)
                .evaluate("内部代号是什么？", List.of(chunk("内部代号为 QYH-6。")), "  ");

        assertEquals(0.85d, result.getFaithfulness());
        assertNull(result.getCorrectness());
        assertEquals(GenerationEvalService.NO_REFERENCE_REASON, result.getCorrectnessReason());
        assertEquals(2, client.calls.size());
        assertEquals("generate", client.calls.get(0).kind);
        assertEquals("judge", client.calls.get(1).kind);
    }

    @Test
    void unparsableFaithfulnessKeepsAnswerAndCorrectness() {
        RecordingClient client = new RecordingClient();
        client.faithfulnessRaw = "无法判断";
        GenerationEvalResult result = service(true, client)
                .evaluate("内部代号是什么？", List.of(chunk("内部代号为 QYH-6。")), "内部代号为 QYH-6。");

        assertEquals("内部代号是 QYH-6。", result.getAnswer());
        assertNull(result.getFaithfulness());
        assertTrue(result.getFaithfulnessReason().contains("忠实度裁判失败"));
        assertEquals(1.0d, result.getCorrectness());
    }

    @Test
    void parserAcceptsFencedJsonAndRejectsOutOfRangeScore() {
        JudgeScoreParser parser = new JudgeScoreParser(objectMapper);
        JudgeScoreParser.JudgeScore score = parser.parse("""
                ```json
                {"score": 0.85, "reason": "答案准确引用了资料中的核心事实，但遗漏了一个条件。"}
                ```
                """);

        assertEquals(0.85d, score.score());
        assertEquals("答案准确引用了资料中的核心事实，但遗漏了一个条件。", score.reason());
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{\"score\": 1.2, \"reason\": \"超出\"}"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("不是 JSON"));
    }

    @Test
    void cleanerRemovesCiteMarks() {
        assertEquals("内部代号是 QYH-6。",
                AnswerMarkCleaner.clean("内部代号是 QYH-6。<cite id=\"123\"/>"));
        assertEquals("内部代号是 QYH-6。",
                AnswerMarkCleaner.clean("内部代号是 QYH-6。<cite id=\"123\" />"));
    }

    private GenerationEvalService service(boolean enabled, RecordingClient client) {
        return new GenerationEvalService(new PromptServiceImpl(), client, objectMapper, enabled);
    }

    private static RetrievedChunk chunk(String content) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setDocumentId(11L);
        chunk.setChunkId(22L);
        chunk.setContent(content);
        chunk.setPage(1);
        return chunk;
    }

    private static String userText(List<ChatMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                text.append(userMessage.singleText());
            }
        }
        return text.toString();
    }

    private static final class RecordingClient implements EvalChatClient {

        private final List<Call> calls = new ArrayList<>();
        private String faithfulnessRaw = """
                {"score": 0.85, "reason": "答案准确引用了资料中的核心事实，但遗漏了一个条件。"}
                """;

        @Override
        public String generationModelName() {
            return "qwen-plus";
        }

        @Override
        public String judgeModelName() {
            return "qwen3.8-flash";
        }

        @Override
        public String generate(List<ChatMessage> messages) {
            calls.add(new Call("generate", messages));
            return "内部代号是 QYH-6。<cite id=\"2116158479097040897\"/>";
        }

        @Override
        public String judge(List<ChatMessage> messages) {
            calls.add(new Call("judge", messages));
            String text = userText(messages);
            if (text.contains("【标准答案】")) {
                return "{\"score\": 1, \"reason\": \"与标准答案一致。\"}";
            }
            return faithfulnessRaw;
        }

        private record Call(String kind, List<ChatMessage> messages) {
        }
    }
}
