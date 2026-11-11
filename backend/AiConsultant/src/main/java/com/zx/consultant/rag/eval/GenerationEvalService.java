package com.zx.consultant.rag.eval;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.common.exception.LLMException;
import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.rag.entity.RetrievedChunk;
import com.zx.consultant.rag.service.PromptService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 单题生成评测：用本次召回结果拼 RAG 提示词，被测模型作答，再分别做忠实度和正确性裁判。
 * 评测运行只在开关打开时调用。检索为空时不调用模型。
 */
@Slf4j
@Service
public class GenerationEvalService {

    /** 改裁判提示词时递增，并写入评测配置快照 */
    public static final String JUDGE_PROMPT_VERSION = "v1";

    /** 与线上 {@code RetrieveNode} 的空检索回复一致 */
    static final String EMPTY_RETRIEVAL_ANSWER = "知识库中暂未找到相关信息";

    static final String EMPTY_RETRIEVAL_REASON = "检索为空，未调用模型";

    static final String NO_REFERENCE_REASON = "没有标准答案，未调用正确性裁判";

    private static final String JUDGE_OUTPUT_RULE = """
            只输出一个 JSON 对象，不要使用 Markdown 代码块，不要输出其他文字。
            格式：{"score": 0.85, "reason": "一句中文理由"}
            score 取 0 到 1 之间的小数。
            """;

    private static final String FAITHFULNESS_SYSTEM = """
            你是评测裁判。只根据【参考资料】判断【答案】里的事实有没有依据。
            不要使用参考资料以外的知识，也不要改写答案。
            答案明确表示资料不足，且没有编造事实时，score 为 1。
            答案写出了资料中没有的事实时，按编造程度降低 score。
            """ + JUDGE_OUTPUT_RULE;

    private static final String CORRECTNESS_SYSTEM = """
            你是评测裁判。比较【答案】和【标准答案】是否表达了相同的事实，换一种说法也算对。
            漏掉标准答案中的事实要降低 score。多写了解释、但没有说错事实时，不要把 score 打成 0。
            """ + JUDGE_OUTPUT_RULE;

    private final PromptService promptService;
    private final EvalChatClient evalChatClient;
    private final JudgeScoreParser judgeScoreParser;
    private final boolean generationEnabled;

    public GenerationEvalService(
            PromptService promptService,
            EvalChatClient evalChatClient,
            ObjectMapper objectMapper,
            @Value("${app.rag.eval.generation-enabled:false}") boolean generationEnabled) {
        this.promptService = promptService;
        this.evalChatClient = evalChatClient;
        this.judgeScoreParser = new JudgeScoreParser(objectMapper);
        this.generationEnabled = generationEnabled;
    }

    public boolean generationEnabled() {
        return generationEnabled;
    }

    public String judgePromptVersion() {
        return JUDGE_PROMPT_VERSION;
    }

    public String generationModelName() {
        return evalChatClient.generationModelName();
    }

    public String judgeModelName() {
        return evalChatClient.judgeModelName();
    }

    /**
     * @param query 原题，不经过查询改写
     * @param chunks 本次检索的 finalTopK，可以是空列表
     * @param groundTruthAnswer 标准答案。空白时只评忠实度，检索为空时仍把正确性记为 0
     */
    public GenerationEvalResult evaluate(String query, List<RetrievedChunk> chunks, String groundTruthAnswer) {
        if (!generationEnabled) {
            throw new IllegalStateException("生成评测未开启，配置 app.rag.eval.generation-enabled=false");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("评测问题为空");
        }

        List<RetrievedChunk> contextChunks = chunks == null ? List.of() : chunks;
        if (contextChunks.isEmpty()) {
            return emptyRetrieval();
        }
        PromptRequest prompt = promptService.buildRagPrompt(query, List.of(), contextChunks);
        String rawAnswer = evalChatClient.generate(EvalPromptMessages.toChatMessages(prompt));
        String answer = AnswerMarkCleaner.clean(rawAnswer);

        GenerationEvalResult result = new GenerationEvalResult();
        result.setAnswer(answer);
        result.setGenerationModel(evalChatClient.generationModelName());
        result.setJudgeModel(evalChatClient.judgeModelName());
        fillFaithfulness(result, prompt.getContext(), answer);
        fillCorrectness(result, query, answer, groundTruthAnswer);
        return result;
    }

    /** 线上空检索不进模型。忠实度留空，正确性记 0 */
    private GenerationEvalResult emptyRetrieval() {
        GenerationEvalResult result = new GenerationEvalResult();
        result.setAnswer(EMPTY_RETRIEVAL_ANSWER);
        result.setFaithfulness(null);
        result.setFaithfulnessReason(EMPTY_RETRIEVAL_REASON);
        result.setCorrectness(0d);
        result.setCorrectnessReason(EMPTY_RETRIEVAL_REASON);
        result.setGenerationModel(evalChatClient.generationModelName());
        result.setJudgeModel(evalChatClient.judgeModelName());
        return result;
    }

    private void fillFaithfulness(GenerationEvalResult result, String context, String answer) {
        String user = """
                【参考资料】
                %s

                【答案】
                %s
                """.formatted(context == null ? "" : context, answer);
        try {
            JudgeScoreParser.JudgeScore score = judgeScoreParser.parse(evalChatClient.judge(List.of(
                    SystemMessage.from(FAITHFULNESS_SYSTEM),
                    UserMessage.from(user))));
            result.setFaithfulness(score.score());
            result.setFaithfulnessReason(score.reason());
        } catch (LLMException | IllegalArgumentException e) {
            log.warn("忠实度裁判失败: {}", e.getMessage());
            result.setFaithfulness(null);
            result.setFaithfulnessReason("忠实度裁判失败: " + e.getMessage());
        }
    }

    /** 正确性只看问题、标准答案和答案，不把检索资料交给裁判 */
    private void fillCorrectness(GenerationEvalResult result, String query, String answer, String groundTruthAnswer) {
        if (groundTruthAnswer == null || groundTruthAnswer.isBlank()) {
            result.setCorrectness(null);
            result.setCorrectnessReason(NO_REFERENCE_REASON);
            return;
        }
        String user = """
                【问题】
                %s

                【标准答案】
                %s

                【答案】
                %s
                """.formatted(query, groundTruthAnswer.trim(), answer);
        try {
            JudgeScoreParser.JudgeScore score = judgeScoreParser.parse(evalChatClient.judge(List.of(
                    SystemMessage.from(CORRECTNESS_SYSTEM),
                    UserMessage.from(user))));
            result.setCorrectness(score.score());
            result.setCorrectnessReason(score.reason());
        } catch (LLMException | IllegalArgumentException e) {
            log.warn("正确性裁判失败: {}", e.getMessage());
            result.setCorrectness(null);
            result.setCorrectnessReason("正确性裁判失败: " + e.getMessage());
        }
    }
}
