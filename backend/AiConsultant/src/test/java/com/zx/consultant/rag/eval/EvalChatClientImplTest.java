package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

/**
 * 确认评测客户端自己持有温度 0 的模型，关闭开关时不会发起调用。
 */
class EvalChatClientImplTest {

    @Test
    void clientsFixTemperatureAtZero() {
        EvalChatClientImpl client = client(true);

        assertEquals("qwen-plus", client.generationModelName());
        assertEquals("deepseek-flash", client.faithfulnessJudgeModelName());
        assertEquals("qwen3.8-flash", client.correctnessJudgeModelName());
        assertEquals(EvalChatClientImpl.EVAL_TEMPERATURE, client.generationParameters().temperature());
        assertEquals(EvalChatClientImpl.EVAL_TEMPERATURE, client.faithfulnessParameters().temperature());
        assertEquals(EvalChatClientImpl.EVAL_TEMPERATURE, client.correctnessParameters().temperature());
        assertEquals("qwen-plus", client.generationParameters().modelName());
        assertEquals("deepseek-flash", client.faithfulnessParameters().modelName());
        assertEquals("qwen3.8-flash", client.correctnessParameters().modelName());
    }

    @Test
    void deepSeekRequestDisablesThinking() {
        String body = ThinkingDisabledHttpClient.withThinkingDisabled("{\"model\":\"deepseek-flash\",\"temperature\":0}");

        assertTrue(body.contains("\"thinking\""));
        assertTrue(body.contains("\"disabled\""));
        assertTrue(body.contains("\"deepseek-flash\""));
    }

    @Test
    void disabledClientRefusesBeforeRequest() {
        EvalChatClientImpl client = client(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.generate(List.of(UserMessage.from("内部代号是什么？"))));

        assertTrue(error.getMessage().contains("generation-enabled"));
        assertThrows(IllegalStateException.class,
                () -> client.judgeFaithfulness(List.of(UserMessage.from("打分"))));
        assertThrows(IllegalStateException.class,
                () -> client.judgeCorrectness(List.of(UserMessage.from("打分"))));
    }

    private static EvalChatClientImpl client(boolean enabled) {
        return new EvalChatClientImpl(
                enabled,
                "qwen-plus",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "test-key",
                "qwen3.8-flash",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "test-key",
                "deepseek-flash",
                "https://api.deepseek.com",
                "test-key",
                false,
                false);
    }
}
