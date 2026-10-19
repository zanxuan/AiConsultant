package com.zx.consultant.rag.eval;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.common.result.PageResult;
import com.zx.consultant.common.result.Result;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.rag.eval.vo.EvalRunExecuteVO;
import com.zx.consultant.rag.eval.vo.RagEvalRetrievalTraceVO;
import com.zx.consultant.rag.eval.vo.RagEvalRunDetailVO;
import com.zx.consultant.rag.eval.vo.RagEvalRunVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 现有离线 RAG 评测的 HTTP 入口。发起评测时在原有结果外返回 runId，供详情页按记录打开。
 */
@RestController
@RequestMapping("/api/v1/eval")
@RequiredArgsConstructor
@Tag(name = "RAG 评测", description = "触发现有离线检索评测，并按记录 ID 查看历史详情")
public class RagEvalController {

    private final RagEvalService ragEvalService;
    private final RagEvalRunService ragEvalRunService;
    private final RagEvalRetrievalTraceService retrievalTraceService;

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
     * 旧入口：不上传文件时仍跑固定 golden-set。
     * knowledgeId 为空则沿用 app.rag.eval.knowledge-id。
     */
    @PostMapping("/run")
    @Operation(summary = "运行离线 RAG 评测", description = "同步执行固定 golden-set，返回 runId 与评测结果")
    public Result<EvalRunExecuteVO> run(
            @Parameter(description = "评测知识库 ID，对应 app.rag.eval.knowledge-id")
            @RequestParam(value = "knowledgeId", required = false) Long knowledgeId) {
        try {
            return Result.success(ragEvalService.executeGoldenSet(knowledgeId));
        } catch (IllegalStateException e) {
            throw new BaseException(e.getMessage());
        }
    }

    /**
     * 新入口：上传测试集 JSON，并指定知识库 ID。解析后走同一套评测核心。
     */
    @PostMapping(value = "/run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传测试集并评测", description = "multipart 提交 dataset 与 knowledgeId，返回 runId 与评测结果")
    public Result<EvalRunExecuteVO> runUploaded(
            @Parameter(description = "EvalCase JSON 数组，格式与 golden-set 一致")
            @RequestParam("dataset") MultipartFile dataset,
            @Parameter(description = "知识库 ID")
            @RequestParam("knowledgeId") Long knowledgeId) {
        if (dataset == null || dataset.isEmpty()) {
            throw new BaseException("请上传测试集");
        }
        if (knowledgeId == null) {
            throw new BaseException("请选择知识库");
        }
        String sourceName = dataset.getOriginalFilename();
        if (sourceName == null || sourceName.isBlank()) {
            sourceName = "uploaded-dataset";
        }
       
        try (InputStream input = dataset.getInputStream()) {
            List<EvalCase> cases = ragEvalService.parseCases(input, sourceName);
                 
            return Result.success(ragEvalService.execute(cases, knowledgeId, sourceName));
        } catch (IllegalStateException e) {
            throw new BaseException(e.getMessage());
        } catch (IOException e) {
            throw new BaseException("读取测试集失败");
        }
    }

    /**
     * 当前登录用户的评测历史。不返回测试集和结果快照。
     */
    @GetMapping("/runs")
    @Operation(summary = "分页查询评测历史", description = "只返回当前用户的记录，可按知识库和状态过滤")
    public Result<PageResult<RagEvalRunVO>> runs(
            @Parameter(description = "知识库 ID")
            @RequestParam(value = "knowledgeId", required = false) Long knowledgeId,
            @Parameter(description = "RUNNING / SUCCESS / FAILED")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数")
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.success(ragEvalRunService.pageRuns(
                BaseContext.getCurrentId(), knowledgeId, status, pageNum, pageSize));
    }

    /**
     * 详情校验记录归属，不能只凭 ID 读取。
     */
    @GetMapping("/runs/{id}")
    @Operation(summary = "查询评测历史详情", description = "仅记录所属用户可查看用例快照和完整结果")
    public Result<RagEvalRunDetailVO> runDetail(
            @Parameter(description = "评测记录 ID", required = true)
            @PathVariable("id") Long id) {
        return Result.success(ragEvalRunService.getDetail(id, BaseContext.getCurrentId()));
    }

    /**
     * 按运行和用例读取已保存的检索链路。不重新检索，也不改原详情接口。
     */
    @GetMapping("/runs/{id}/cases/{caseId}/retrieval-trace")
    @Operation(summary = "查询用例检索链路", description = "仅记录所属用户可查看该用例已保存的检索快照")
    public Result<RagEvalRetrievalTraceVO> retrievalTrace(
            @Parameter(description = "评测记录 ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "用例 ID", required = true)
            @PathVariable("caseId") Integer caseId) {
        return Result.success(retrievalTraceService.getTrace(id, caseId, BaseContext.getCurrentId()));
    }
}
