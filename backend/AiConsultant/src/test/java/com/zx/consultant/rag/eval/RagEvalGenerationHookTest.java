package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.common.exception.LLMException;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.retriever.HybridRetriever;
import com.zx.consultant.rag.service.impl.PromptServiceImpl;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

/**
 * 生成评测接在同一次检索后面。关闭时不调用模型，打开时单题失败不影响检索指标。
 */
class RagEvalGenerationHookTest {

    private static final double DELTA = 1e-9d;

    @Test
    void disabledGenerationLeavesRetrievalUntouched() {
        ScriptedClient client = new ScriptedClient();
        RecordingRunService runs = new RecordingRunService();
        EvalResult result = run(client, runs, false);

        assertEquals(2.0d / 3.0d, result.getHitRate(), DELTA);
        assertEquals(2.0d / 3.0d, result.getRecall(), DELTA);
        assertEquals(2.0d / 3.0d, result.getMrr(), DELTA);
        assertNull(result.getFaithfulness());
        assertNull(result.getCorrectness());
        assertNull(result.getCases().get(0).getAnswer());
        assertTrue(client.calls.isEmpty());
        assertEquals(false, runs.config.get("generationEnabled"));
        assertEquals(GenerationEvalService.JUDGE_PROMPT_VERSION, runs.config.get("judgePromptVersion"));
        assertNull(runs.config.get("generationModel"));
    }

    @Test
    void enabledGenerationKeepsRetrievalAndIsolatesFailure() {
        ScriptedClient client = new ScriptedClient();
        RecordingRunService runs = new RecordingRunService();
        EvalResult result = run(client, runs, true);

        assertEquals(2.0d / 3.0d, result.getHitRate(), DELTA);
        assertEquals(2.0d / 3.0d, result.getRecall(), DELTA);
        assertEquals(2.0d / 3.0d, result.getMrr(), DELTA);
        assertEquals(0.85d, result.getFaithfulness(), DELTA);
        assertEquals(0.5d, result.getCorrectness(), DELTA);

        EvalCaseResult scored = result.getCases().get(0);
        assertTrue(scored.isHit());
        assertEquals("内部代号是 QYH-6。", scored.getAnswer());
        assertEquals(0.85d, scored.getFaithfulness());
        assertEquals(1.0d, scored.getCorrectness());
        assertTrue(scored.getLatencyMs() < 80L);

        EvalCaseResult empty = result.getCases().get(1);
        assertEquals(false, empty.isHit());
        assertEquals(GenerationEvalService.EMPTY_RETRIEVAL_ANSWER, empty.getAnswer());
        assertNull(empty.getFaithfulness());
        assertEquals(0.0d, empty.getCorrectness());

        EvalCaseResult failed = result.getCases().get(2);
        assertTrue(failed.isHit());
        assertNull(failed.getAnswer());
        assertNull(failed.getFaithfulness());
        assertNull(failed.getCorrectness());

        assertEquals(List.of("generate:ok", "judge:faith", "judge:correct", "generate:fail"), client.calls);
        assertTrue(client.faithfulnessText.contains("资料原句QYH-6"));
        assertFalse(client.correctnessText.contains("资料原句QYH-6"));
        assertTrue(client.correctnessText.contains("内部代号为 QYH-6。"));
        assertEquals(true, runs.config.get("generationEnabled"));
        assertEquals("v1", runs.config.get("judgePromptVersion"));
        assertEquals("qwen-plus", runs.config.get("generationModel"));
        assertEquals("deepseek-flash", runs.config.get("faithfulnessJudgeModel"));
        assertEquals("qwen3.8-flash", runs.config.get("correctnessJudgeModel"));
    }

    private static EvalResult run(ScriptedClient client, RecordingRunService runs, boolean enabled) {
        RagEvalService service = new RagEvalService(
                new FixedRetriever(), new ObjectMapper(), new NoopWriter(), runs, null, null);
        service.setGenerationEvalService(new GenerationEvalService(
                new PromptServiceImpl(), client, new ObjectMapper(), enabled));
        return service.evaluate(List.of(foundCase(), emptyCase(), failingCase()), 1L, "generation.json");
    }

    private static EvalCase foundCase() {
        EvalCase evalCase = new EvalCase();
        evalCase.setId(1);
        evalCase.setQuery("有资料");
        evalCase.setExpectedDocIds(List.of("11"));
        evalCase.setGroundTruthAnswer("内部代号为 QYH-6。");
        return evalCase;
    }

    private static EvalCase emptyCase() {
        EvalCase evalCase = new EvalCase();
        evalCase.setId(2);
        evalCase.setQuery("没资料");
        evalCase.setExpectedDocIds(List.of("11"));
        evalCase.setGroundTruthAnswer("内部代号为 QYH-6。");
        return evalCase;
    }

    private static EvalCase failingCase() {
        EvalCase evalCase = new EvalCase();
        evalCase.setId(3);
        evalCase.setQuery("生成失败");
        evalCase.setExpectedDocIds(List.of("11"));
        evalCase.setGroundTruthAnswer("内部代号为 QYH-6。");
        return evalCase;
    }

    private static final class FixedRetriever extends HybridRetriever {
        private FixedRetriever() {
            super(null, null);
        }

        @Override
        public RecallTrace diagnose(String query, Long knowledgeId) {
            RecallTrace trace = new RecallTrace();
            if ("没资料".equals(query)) {
                trace.finalTopK = new ArrayList<>();
            } else {
                RetrievedChunk chunk = new RetrievedChunk();
                chunk.setDocumentId(11L);
                chunk.setChunkId(22L);
                chunk.setContent("资料原句QYH-6");
                chunk.setFinalScore(0.9d);
                trace.finalTopK = new ArrayList<>(List.of(chunk));
                trace.vectorHits = new ArrayList<>(List.of(chunk));
            }
            trace.bm25Hits = List.of();
            trace.mergedByRrf = new ArrayList<>(trace.finalTopK);
            trace.afterFilter = new ArrayList<>(trace.finalTopK);
            return trace;
        }
    }

    private static final class ScriptedClient implements EvalChatClient {
        private final List<String> calls = new ArrayList<>();
        private String faithfulnessText = "";
        private String correctnessText = "";

        @Override
        public String generationModelName() {
            return "qwen-plus";
        }

        @Override
        public String faithfulnessJudgeModelName() {
            return "deepseek-flash";
        }

        @Override
        public String correctnessJudgeModelName() {
            return "qwen3.8-flash";
        }

        @Override
        public String generate(List<ChatMessage> messages) {
            String text = userText(messages);
            if (text.contains("生成失败")) {
                calls.add("generate:fail");
                throw new LLMException("生成超时");
            }
            calls.add("generate:ok");
            try {
                Thread.sleep(80L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "内部代号是 QYH-6。<cite id=\"11\"/>";
        }

        @Override
        public String judgeFaithfulness(List<ChatMessage> messages) {
            calls.add("judge:faith");
            faithfulnessText = userText(messages);
            return "{\"score\": 0.85, \"reason\": \"依据了资料。\"}";
        }

        @Override
        public String judgeCorrectness(List<ChatMessage> messages) {
            calls.add("judge:correct");
            correctnessText = userText(messages);
            return "{\"score\": 1, \"reason\": \"与标准答案一致。\"}";
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
    }

    private static final class RecordingRunService extends RagEvalRunService {
        private Map<String, Object> config;

        private RecordingRunService() {
            super(new ObjectMapper());
        }

        @Override
        public Long createRunning(Long userId, Long knowledgeId, String modelName, String knowledgeName,
                RagEvalDatasetSource datasetSource, String datasetName, List<EvalCase> cases,
                Map<String, Object> configSnapshot) {
            this.config = configSnapshot;
            return 3L;
        }

        @Override
        public void markSuccess(Long runId, EvalResult result, long durationMs, String modelName,
                String knowledgeName) {
        }

        @Override
        public void markFailed(Long runId, EvalResult result, long durationMs, Throwable error,
                String modelName, String knowledgeName) {
        }
    }

    private static final class NoopWriter extends EvalReportWriter {
        private NoopWriter() {
            super(null);
        }

        @Override
        public java.nio.file.Path write(EvalResult result, EvalReportMeta meta) {
            return java.nio.file.Path.of("noop");
        }
    }
}
