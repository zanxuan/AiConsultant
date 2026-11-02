package com.zx.consultant.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 报告文件名和开头信息不落盘，避免测试写进 docs/eval/result。
 */
class EvalReportWriterTest {

    @Test
    void fileNameUsesTimeAndRunId() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 2, 10, 54, 30);

        assertEquals("eval-result-2026-11-02-105430-11.md", EvalReportWriter.fileName(time, 11L));
        assertEquals("eval-result-2026-11-02-105430.md", EvalReportWriter.fileName(time, null));
    }

    @Test
    void headerIncludesRunDatasetAndKnowledge() {
        EvalResult result = summary();
        EvalReportMeta golden = new EvalReportMeta(
                11L, "golden-set.json", RagEvalDatasetSource.GOLDEN_SET, 9L, "货运知识库");
        EvalReportMeta uploaded = new EvalReportMeta(
                12L, "自定义\n测试.json", RagEvalDatasetSource.UPLOAD, 9L, "货运知识库");

        String goldenText = new EvalReportWriter(null).render(result, golden);
        String uploadedText = new EvalReportWriter(null).render(result, uploaded);

        assertTrue(goldenText.contains("Run Id: 11"));
        assertTrue(goldenText.contains("Dataset: golden-set.json"));
        assertTrue(goldenText.contains("Dataset Source: 固定 Golden Set"));
        assertTrue(goldenText.contains("Knowledge Id: 9"));
        assertTrue(goldenText.contains("Knowledge: 货运知识库"));
        assertTrue(uploadedText.contains("Dataset: 自定义 测试.json"));
        assertTrue(uploadedText.contains("Dataset Source: 上传测试集"));
    }

    @Test
    void missingMetaStaysOnOneLine() {
        String text = new EvalReportWriter(null).render(summary(), null);

        assertTrue(text.contains("Run Id: N/A"));
        assertTrue(text.contains("Dataset: N/A"));
        assertTrue(text.contains("Dataset Source: N/A"));
        assertTrue(text.contains("Knowledge Id: N/A"));
        assertTrue(text.contains("Knowledge: N/A"));
    }

    private static EvalResult summary() {
        EvalResult result = new EvalResult();
        result.setTotal(2);
        result.setHitRate(0.5d);
        result.setRecall(0.5d);
        result.setMrr(0.5d);
        return result;
    }
}
