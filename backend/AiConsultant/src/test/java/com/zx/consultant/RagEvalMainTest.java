package com.zx.consultant;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import com.zx.consultant.common.config.LocalDotenvBootstrap;
import com.zx.consultant.rag.eval.RagEvalService;

/**
 * RAG 评测入口：跑完打印 Hit Rate / Recall@5 / MRR 后退出。
 * <p>
 * 在 IDE 里直接运行 main 即可。
 */
public class RagEvalMainTest {

    /** 知识库：mysql与redis */
    private static final String KNOWLEDGE_ID = "2084568960212553729";

    public static void main(String[] args) {
        LocalDotenvBootstrap.load();

        ConfigurableApplicationContext context = SpringApplication.run(
                ConsultantApplication.class,
                "--app.rag.eval.knowledge-id=" + KNOWLEDGE_ID);

        try {
            RagEvalService ragEvalService = context.getBean(RagEvalService.class);
            ragEvalService.evaluate();
        } finally {
            context.close();
        }
    }
}
