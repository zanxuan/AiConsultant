package com.zx.consultant.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.common.trace.NodeSpan;
import com.zx.consultant.trace.dto.TraceDetailResp;
import com.zx.consultant.trace.entity.TraceSpan;
import com.zx.consultant.trace.mapper.TraceSpanMapper;
import com.zx.consultant.trace.service.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private static final int ERROR_MESSAGE_MAX_LEN = 1024;

    private final TraceSpanMapper traceSpanMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSpans(String traceId, List<NodeSpan> spans) {
        if (traceId == null || traceId.isBlank() || spans == null || spans.isEmpty()) {
            return;
        }
        for (NodeSpan span : spans) {
            TraceSpan entity = new TraceSpan();
            entity.setTraceId(traceId);
            entity.setNodeName(span.getNodeName());
            entity.setCostTime(span.getCostMs());
            entity.setStatus(span.getStatus() != null ? span.getStatus().name() : null);
            entity.setErrorMessage(truncate(span.getErrorMessage()));
            entity.setModelUsed(span.getModelUsed());
            entity.setFallbackTriggered(span.getFallbackTriggered());
            entity.setFallbackReason(truncate(span.getFallbackReason()));
            traceSpanMapper.insert(entity);
        }
        log.info("Trace spans persisted: traceId={}, count={}", traceId, spans.size());
    }

    @Override
    public TraceDetailResp getByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new BaseException("traceId 不能为空");
        }
        List<TraceSpan> rows = traceSpanMapper.selectList(
                new LambdaQueryWrapper<TraceSpan>()
                        .eq(TraceSpan::getTraceId, traceId)
                        .orderByAsc(TraceSpan::getId));
        if (rows.isEmpty()) {
            throw new BaseException("未找到该 traceId 对应的链路记录");
        }

        List<TraceDetailResp.SpanItem> spans = rows.stream()
                .map(row -> TraceDetailResp.SpanItem.builder()
                        .id(row.getId())
                        .nodeName(row.getNodeName())
                        .costTime(row.getCostTime())
                        .status(row.getStatus())
                        .errorMessage(row.getErrorMessage())
                        .modelUsed(row.getModelUsed())
                        .fallbackTriggered(row.getFallbackTriggered())
                        .fallbackReason(row.getFallbackReason())
                        .createTime(row.getCreateTime())
                        .build())
                .toList();

        long total = spans.stream()
                .mapToLong(s -> s.getCostTime() != null ? s.getCostTime() : 0L)
                .sum();

        return TraceDetailResp.builder()
                .traceId(traceId)
                .totalCostTime(total)
                .spans(spans)
                .build();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= ERROR_MESSAGE_MAX_LEN) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_MAX_LEN);
    }
}
