package com.zx.consultant.rag.eval;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动参数带 {@code --eval} 时跑一轮 RAG 评测并打印指标，不引入额外中间件。
 * <p>
 * 示例：{@code --eval --app.rag.eval.knowledge-id=1}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagEvalRunner implements ApplicationRunner {

    private final RagEvalService ragEvalService;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("eval") && !args.getNonOptionArgs().contains("eval")) {
            return;
        }
        log.info("检测到 --eval，开始 RAG Evaluation");
        ragEvalService.evaluate();
    }
}
