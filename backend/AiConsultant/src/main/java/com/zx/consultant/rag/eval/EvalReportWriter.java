package com.zx.consultant.rag.eval;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;
import com.zx.consultant.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 把评测结果写成 Markdown 报告。
 * <p>
 * 每次运行单独一个文件：docs/eval/result/eval-result-yyyy-MM-dd-HHmmss-{runId}.md。
 * 没有 runId 时文件名只到秒。用例里的文档仍展示 fileName，不展示文档主键。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalReportWriter {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private final DocumentService documentService;

    public Path write(EvalResult result) {
        return write(result, null);
    }

    public Path write(EvalResult result, EvalReportMeta meta) {
        try {
            Long runId = meta == null ? null : meta.getRunId();
            Path out = resolveOutputPath(fileName(LocalDateTime.now(), runId));
            Files.createDirectories(out.getParent());
            Files.writeString(out, render(result, meta));
            log.info("评测报告已写入: {}", out.toAbsolutePath());
            System.out.println("Report: " + out.toAbsolutePath());
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("写入评测报告失败", e);
        }
    }

    /**
     * 文件名带时间和运行记录 ID。同一天再跑会新开一份，已有的按天报告不改名。
     */
    static String fileName(LocalDateTime time, Long runId) {
        String name = "eval-result-" + time.format(FILE_TIME);
        if (runId != null) {
            name = name + "-" + runId;
        }
        return name + ".md";
    }

    String render(EvalResult result, EvalReportMeta meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("# RAG Evaluation Result\n\n");
        sb.append("Run Id: ").append(idText(meta == null ? null : meta.getRunId())).append("\n\n");
        sb.append("Dataset: ").append(text(meta == null ? null : meta.getDatasetName())).append("\n\n");
        sb.append("Dataset Source: ").append(sourceLabel(meta == null ? null : meta.getDatasetSource())).append("\n\n");
        sb.append("Knowledge Id: ").append(idText(meta == null ? null : meta.getKnowledgeId())).append("\n\n");
        sb.append("Knowledge: ").append(text(meta == null ? null : meta.getKnowledgeName())).append("\n\n");
        sb.append("## Summary\n\n");
        sb.append("Total Cases: ").append(result.getTotal()).append("\n\n");
        sb.append("Hit Rate@5: ").append(formatPercent(result.getHitRate())).append("\n\n");
        sb.append("Recall@5: ").append(formatPercent(result.getRecall())).append("\n\n");
        sb.append("MRR: ").append(String.format("%.2f", result.getMrr())).append("\n\n");
        sb.append("Avg Latency: ").append(formatLatencyMs(result.getAvgLatencyMs())).append("\n\n");
        sb.append("P95 Latency: ").append(formatLatencyMs(result.getP95LatencyMs())).append("\n\n");
        if (result.getFaithfulness() != null) {
            sb.append("Faithfulness: ").append(formatPercent(result.getFaithfulness())).append("\n\n");
        }
        if (result.getCorrectness() != null) {
            sb.append("Correctness: ").append(formatPercent(result.getCorrectness())).append("\n\n");
        }
        if (result.getRerankFallbackCount() != null) {
            sb.append("Rerank Fallback: ").append(result.getRerankFallbackCount()).append("\n\n");
        }
        sb.append("---\n\n");
        sb.append("# Failed Cases\n\n");

        List<FailedCase> failed = result.getFailedCases();
        if (failed == null || failed.isEmpty()) {
            sb.append("无失败用例。\n");
        } else {
            int i = 1;
            for (FailedCase item : failed) {
                sb.append("## Case ").append(i++).append("\n\n");
                if (item.getId() != null) {
                    sb.append("Case Id: ").append(item.getId()).append("\n\n");
                }
                sb.append("### Query\n\n");
                sb.append(item.getQuery()).append("\n\n");
                sb.append("### Expected Documents\n\n");
                appendList(sb, item.getExpectedDocIds());
                sb.append("\n");
                sb.append("### Retrieved Documents\n\n");
                appendNumbered(sb, item.getRetrievedDocIds());
                sb.append("\n");
                sb.append("### Top Score\n\n");
                if (item.getTopScore() == null) {
                    sb.append("N/A\n\n");
                } else {
                    sb.append(String.format("%.2f", item.getTopScore())).append("\n\n");
                }
                sb.append("### Retrieve Latency\n\n");
                sb.append(formatLatencyMs(item.getLatencyMs())).append("\n\n");
                if (Boolean.TRUE.equals(item.getRerankFallback())) {
                    sb.append("### Rerank Fallback\n\n");
                    sb.append(text(item.getRerankFallbackReason())).append("\n\n");
                }
                sb.append("---\n\n");
            }
        }

        return sb.toString();
    }

    private void appendList(StringBuilder sb, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            sb.append("- (empty)\n");
            return;
        }
        for (String id : ids) {
            sb.append("- ").append(toDocName(id)).append("\n");
        }
    }

    private void appendNumbered(StringBuilder sb, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            sb.append("(empty)\n");
            return;
        }
        int n = 1;
        for (String id : ids) {
            sb.append(n++).append(". ").append(toDocName(id)).append("\n");
        }
    }

    /** documentId → fileName，查不到则回退原 ID */
    private String toDocName(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return "(unknown)";
        }
        try {
            return documentService.getDocumentName(Long.parseLong(documentId.trim()));
        } catch (Exception e) {
            return documentId;
        }
    }

    private String formatPercent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    private static String formatLatencyMs(Long latencyMs) {
        if (latencyMs == null) {
            return "N/A";
        }
        return latencyMs + " ms";
    }

    private static String sourceLabel(RagEvalDatasetSource source) {
        if (source == RagEvalDatasetSource.GOLDEN_SET) {
            return "固定 Golden Set";
        }
        if (source == RagEvalDatasetSource.UPLOAD) {
            return "上传测试集";
        }
        return "N/A";
    }

    private static String idText(Long id) {
        return id == null ? "N/A" : id.toString();
    }

    /** 空值和换行都收成单行，避免上传文件名把报告字段拆开。 */
    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return "N/A";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private Path resolveOutputPath(String fileName) {
        String relative = "docs/eval/result/" + fileName;

        Path direct = Path.of(relative);
        if (canWriteUnder(direct.getParent())) {
            return direct;
        }
        Path fromModule = Path.of("..", "..", relative);
        if (canWriteUnder(fromModule.getParent())) {
            return fromModule;
        }
        Path fromBackend = Path.of("..", relative);
        if (canWriteUnder(fromBackend.getParent())) {
            return fromBackend;
        }
        return direct;
    }

    private boolean canWriteUnder(Path parent) {
        if (parent == null) {
            return false;
        }
        File p = parent.toFile();
        return p.isDirectory() || p.getParentFile() != null && p.getParentFile().isDirectory();
    }
}
