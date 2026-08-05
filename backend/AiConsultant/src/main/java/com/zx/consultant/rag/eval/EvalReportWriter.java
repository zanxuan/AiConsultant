package com.zx.consultant.rag.eval;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;
import com.zx.consultant.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 把评测结果写成 Markdown 报告：docs/eval/result/eval-result-yyyy-MM-dd.md
 * <p>
 * 报告里展示文档名（fileName），不展示数据库 ID。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalReportWriter {

    private final DocumentService documentService;

    public Path write(EvalResult result) {
        try {
            Path out = resolveOutputPath();
            Files.createDirectories(out.getParent());

            StringBuilder sb = new StringBuilder();
            sb.append("# RAG Evaluation Result\n\n");
            sb.append("## Summary\n\n");
            sb.append("Total Cases: ").append(result.getTotal()).append("\n\n");
            sb.append("Hit Rate@5: ").append(formatPercent(result.getHitRate())).append("\n\n");
            sb.append("Recall@5: ").append(formatPercent(result.getRecall())).append("\n\n");
            sb.append("MRR: ").append(String.format("%.2f", result.getMrr())).append("\n\n");
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
                    sb.append("---\n\n");
                }
            }

            Files.writeString(out, sb.toString());
            log.info("评测报告已写入: {}", out.toAbsolutePath());
            System.out.println("Report: " + out.toAbsolutePath());
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("写入评测报告失败", e);
        }
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

    private Path resolveOutputPath() {
        String fileName = "eval-result-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
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
