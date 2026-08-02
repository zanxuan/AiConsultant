package com.zx.consultant.trace.controller;

import com.zx.consultant.common.result.Result;
import com.zx.consultant.trace.dto.TraceDetailResp;
import com.zx.consultant.trace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/traces")
@RequiredArgsConstructor
@Tag(name = "Trace 链路查询", description = "按 traceId 查询一次 Chat 请求的完整节点执行链路")
public class TraceController {

    private final TraceService traceService;

    @GetMapping("/{traceId}")
    @Operation(summary = "查询 Trace 链路", description = "返回指定 traceId 下各 Workflow 节点的耗时与状态")
    public Result<TraceDetailResp> getTrace(@PathVariable("traceId") String traceId) {
        return Result.success(traceService.getByTraceId(traceId));
    }
}
