package com.zx.consultant.rag.eval;

import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 现有离线 RAG 评测的 HTTP 入口：同步调用 {@link RagEvalService#evaluate()}，原样返回 {@link EvalResult}。
 */
@RestController
@RequestMapping("/api/v1/eval")
@RequiredArgsConstructor
@Tag(name = "RAG 评测", description = "触发现有离线检索评测并返回 EvalResult")
public class RagEvalController {

    private final RagEvalService ragEvalService;

    /**
     * 读取现有 golden-set.json，不执行评测。
     */
    @GetMapping("/dataset")
    @Operation(summary = "读取评测测试集", description = "返回现有 golden-set 文件名与 EvalCase 列表")
    public Result<EvalDataset> dataset() {
        try {
            return Result.success(ragEvalService.loadDataset());
        } catch (IllegalStateException e) {
            throw new BaseException(e.getMessage());
        }
    }

    /**
     * 同步跑完 golden-set。knowledgeId 对应现有配置 app.rag.eval.knowledge-id；
     * 不传则沿用启动配置，二者都空时仍由 evaluate() 抛出原有异常。
     */
    @PostMapping("/run")
    @Operation(summary = "运行离线 RAG 评测", description = "同步执行现有 evaluate()，返回 Hit Rate / Recall / MRR / failedCases")
    public Result<EvalResult> run(
            @Parameter(description = "评测知识库 ID，对应 app.rag.eval.knowledge-id")
            @RequestParam(value = "knowledgeId", required = false) Long knowledgeId) {
        if (knowledgeId != null) {
            ragEvalService.setKnowledgeId(knowledgeId);
        }
        try {
            return Result.success(ragEvalService.evaluate());
        } catch (IllegalStateException e) {
            throw new BaseException(e.getMessage());
        }
    }
}
