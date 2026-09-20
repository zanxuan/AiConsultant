package com.zx.consultant.llm.service;

import com.zx.consultant.common.exception.LLMException;
import com.zx.consultant.llm.entity.PromptRequest;

import java.util.function.Consumer;

/**
 * 在当前线程阻塞消费 {@link LLMService#streamGenerateAnswer}，直到完整 answer 拼好。
 * Chat 后台任务必须等这里返回后才能落库和发送 complete，不能 subscribe 后提前结束。
 */
public final class LlmStreamCollector {

    private LlmStreamCollector() {
    }

    /**
     * 收集 LLM 流式回答
     * @param llmService LLM 服务
     * @param promptRequest 提示请求
     * @param onPartial 部分回答回调
     * @return 完整回答
     */
    public static String collect(LLMService llmService,
                                 PromptRequest promptRequest,
                                 Consumer<String> onPartial) {
        StringBuilder fullAnswer = new StringBuilder();
        try {
            llmService.streamGenerateAnswer(promptRequest)
                    .doOnNext(partial -> {
                        if (partial == null || partial.isEmpty()) {
                            return;
                        }
                        fullAnswer.append(partial);
                        if (onPartial != null) {
                            onPartial.accept(partial);// 执行外部传入的回调（推SSE消息）
                        }
                    })
                    .blockLast();// 【重点】阻塞当前线程，直到流全部结束/报错
                    //后台任务必须等这里返回之后，才能落库、标记 complete，不能提前结束。
        } catch (RuntimeException e) {
            throw unwrapLlmException(e);
        }
        return fullAnswer.toString();
    }

    /**
     * 解包 LLM 异常
     * @param error 异常
     * @return 解包后的异常
     * 当 Flux 流里面发生错误时，Reactor 不会直接抛出你的原始异常，而是套一层 Reactor 的包装异常
     */
    private static RuntimeException unwrapLlmException(RuntimeException error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LLMException llmException) {
                return llmException;
            }
            current = current.getCause();
        }
        return new LLMException("流式调用失败: " + error.getMessage(), error);
    }
}
