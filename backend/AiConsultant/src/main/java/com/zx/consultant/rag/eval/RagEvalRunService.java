package com.zx.consultant.rag.eval;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.common.result.PageResult;
import com.zx.consultant.rag.eval.entity.RagEvalRun;
import com.zx.consultant.rag.eval.mapper.RagEvalRunMapper;
import com.zx.consultant.rag.eval.vo.RagEvalRunDetailVO;
import com.zx.consultant.rag.eval.vo.RagEvalRunVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 评测历史的写入和查询。指标计算仍在 {@link RagEvalService}。
 * <p>
 * 创建、成功、失败都使用 {@link Propagation#REQUIRES_NEW}，提交独立于外层事务，
 * 避免评测抛错时把失败状态一起回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalRunService extends ServiceImpl<RagEvalRunMapper, RagEvalRun> {

    private static final int ERROR_MESSAGE_LIMIT = 1024;

    private static final TypeReference<List<EvalCase>> CASE_LIST = new TypeReference<>() {};

    private static final TypeReference<Map<String, Object>> CONFIG_MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    /**
     * 校验通过后插入 RUNNING。快照序列化失败时不落库。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createRunning(Long userId, Long knowledgeId, RagEvalDatasetSource datasetSource,
            String datasetName, List<EvalCase> cases, Map<String, Object> configSnapshot) {
        RagEvalRun run = new RagEvalRun();
        run.setUserId(userId);
        run.setKnowledgeId(knowledgeId);
        run.setDatasetSource(datasetSource == null
                ? RagEvalDatasetSource.UPLOAD.name()
                : datasetSource.name());
        run.setDatasetName(StringUtils.hasText(datasetName) ? datasetName : "uploaded-dataset");
        run.setDatasetSnapshot(writeJson(cases));
        run.setConfigSnapshot(writeJson(configSnapshot));
        run.setStatus(RagEvalRunStatus.RUNNING.name());
        this.save(run);
        return run.getId();
    }

    /**
     * 评测正常返回后写入指标和完整结果。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long runId, EvalResult result, long durationMs) {
        if (runId == null) {
            return;
        }
        RagEvalRun update = new RagEvalRun();
        update.setId(runId);
        update.setStatus(RagEvalRunStatus.SUCCESS.name());
        update.setDurationMs(durationMs);
        update.setEndTime(LocalDateTime.now());
        fillMetrics(update, result);
        if (!this.updateById(update)) {
            log.warn("评测记录不存在，无法更新成功状态, runId={}", runId);
        }
    }

    /**
     * 评测中途失败时更新同一行。result 可能为空（检索阶段就抛错）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long runId, EvalResult result, long durationMs, Throwable error) {
        if (runId == null) {
            return;
        }
        RagEvalRun update = new RagEvalRun();
        update.setId(runId);
        update.setStatus(RagEvalRunStatus.FAILED.name());
        update.setDurationMs(durationMs);
        update.setEndTime(LocalDateTime.now());
        update.setErrorMessage(truncateError(error));
        fillMetrics(update, result);
        if (!this.updateById(update)) {
            log.warn("评测记录不存在，无法更新失败状态, runId={}", runId);
        }
    }

    /**
     * 只返回当前用户的记录。knowledgeId、status 可选。
     */
    public PageResult<RagEvalRunVO> pageRuns(Long userId, Long knowledgeId, String status,
            Integer pageNum, Integer pageSize) {
        requireUser(userId);
        if (StringUtils.hasText(status)) {
            parseStatus(status);
        }
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<RagEvalRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(
                RagEvalRun::getId,
                RagEvalRun::getKnowledgeId,
                RagEvalRun::getDatasetSource,
                RagEvalRun::getDatasetName,
                RagEvalRun::getStatus,
                RagEvalRun::getTotalCases,
                RagEvalRun::getHitRate,
                RagEvalRun::getRecallAtK,
                RagEvalRun::getMrr,
                RagEvalRun::getAvgLatencyMs,
                RagEvalRun::getP95LatencyMs,
                RagEvalRun::getFailedCount,
                RagEvalRun::getDurationMs,
                RagEvalRun::getErrorMessage,
                RagEvalRun::getCreateTime,
                RagEvalRun::getEndTime);
        wrapper.eq(RagEvalRun::getUserId, userId);
        if (knowledgeId != null) {
            wrapper.eq(RagEvalRun::getKnowledgeId, knowledgeId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(RagEvalRun::getStatus, status);
        }
        wrapper.orderByDesc(RagEvalRun::getCreateTime).orderByDesc(RagEvalRun::getId);

        Page<RagEvalRun> page = this.page(new Page<>(current, size), wrapper);
        List<RagEvalRunVO> voList = page.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(page, voList);
    }

    /**
     * 详情必须属于当前用户。记录不存在、user_id 为空（CLI）或用户不匹配都拒绝。
     */
    public RagEvalRunDetailVO getDetail(Long runId, Long userId) {
        requireUser(userId);
        if (runId == null) {
            throw new BaseException("评测记录不存在");
        }
        RagEvalRun run = this.getById(runId);
        if (run == null) {
            throw new BaseException("评测记录不存在");
        }
        assertOwner(run, userId);

        RagEvalRunDetailVO detail = new RagEvalRunDetailVO();
        copyToVo(run, detail);
        detail.setConfigSnapshot(readConfig(run.getConfigSnapshot()));
        detail.setCases(readCases(run.getDatasetSnapshot()));
        detail.setResult(readResult(run.getResultSnapshot()));
        return detail;
    }

    /**
     * 当前用户与记录归属不一致时拒绝。CLI 记录没有 user_id，同样拒绝。
     */
    void assertOwner(RagEvalRun run, Long userId) {
        if (run == null || userId == null || run.getUserId() == null || !userId.equals(run.getUserId())) {
            throw new BaseException("无权查看该评测记录");
        }
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BaseException("无权查看评测记录");
        }
    }

    private void parseStatus(String status) {
        try {
            RagEvalRunStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BaseException("评测状态无效");
        }
    }

    private void fillMetrics(RagEvalRun update, EvalResult result) {
        if (result == null) {
            return;
        }
        update.setTotalCases(result.getTotal());
        update.setHitRate(result.getHitRate());
        update.setRecallAtK(result.getRecall());
        update.setMrr(result.getMrr());
        update.setAvgLatencyMs(result.getAvgLatencyMs());
        update.setP95LatencyMs(result.getP95LatencyMs());
        List<?> failed = result.getFailedCases();
        update.setFailedCount(failed == null ? 0 : failed.size());
        update.setResultSnapshot(writeJson(result));
    }

    private RagEvalRunVO toVo(RagEvalRun run) {
        RagEvalRunVO vo = new RagEvalRunVO();
        copyToVo(run, vo);
        return vo;
    }

    private void copyToVo(RagEvalRun run, RagEvalRunVO vo) {
        vo.setId(run.getId());
        vo.setKnowledgeId(run.getKnowledgeId());
        vo.setDatasetSource(run.getDatasetSource());
        vo.setDatasetName(run.getDatasetName());
        vo.setStatus(run.getStatus());
        vo.setTotalCases(run.getTotalCases());
        vo.setHitRate(run.getHitRate());
        vo.setRecall(run.getRecallAtK());
        vo.setMrr(run.getMrr());
        vo.setAvgLatencyMs(run.getAvgLatencyMs());
        vo.setP95LatencyMs(run.getP95LatencyMs());
        vo.setFailedCount(run.getFailedCount());
        vo.setDurationMs(run.getDurationMs());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setCreateTime(run.getCreateTime());
        vo.setEndTime(run.getEndTime());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化评测记录失败", e);
        }
    }

    private List<EvalCase> readCases(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<EvalCase> cases = objectMapper.readValue(json, CASE_LIST);
            return cases == null ? List.of() : cases;
        } catch (Exception e) {
            throw new BaseException("评测记录解析失败");
        }
    }

    private Map<String, Object> readConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> config = objectMapper.readValue(json, CONFIG_MAP);
            return config == null ? Map.of() : config;
        } catch (Exception e) {
            throw new BaseException("评测记录解析失败");
        }
    }

    private EvalResult readResult(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, EvalResult.class);
        } catch (Exception e) {
            throw new BaseException("评测记录解析失败");
        }
    }

    private String truncateError(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error.getClass().getSimpleName();
        }
        if (message.length() <= ERROR_MESSAGE_LIMIT) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_LIMIT);
    }
}
