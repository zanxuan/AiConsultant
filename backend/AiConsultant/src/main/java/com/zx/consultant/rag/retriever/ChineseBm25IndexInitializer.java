package com.zx.consultant.rag.retriever;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动时确保独立的中文 BM25 索引存在。
 * <p>
 * 失败只记日志，不阻止应用启动。检索前 {@link BM25Retriever} 会再试一次。
 * 不会删除或重建向量索引 {@code embedding-index}。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ChineseBm25IndexInitializer implements ApplicationRunner {

    private final BM25Retriever bm25Retriever;

    @Override
    public void run(ApplicationArguments args) {
        try {
            bm25Retriever.ensureChineseIndex();
        } catch (RuntimeException e) {
            log.error("中文 BM25 索引初始化失败，检索时会再试一次", e);
        }
    }
}
