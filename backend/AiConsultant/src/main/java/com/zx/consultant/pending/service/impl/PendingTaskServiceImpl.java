package com.zx.consultant.pending.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.common.constant.PendingTaskConstant;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.pending.model.PendingTask;
import com.zx.consultant.pending.service.PendingTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * PendingTask 存 Redis String。只是状态快照，保存时写入 TTL，没有后台轮询线程。
 */
@Slf4j
@Service
public class PendingTaskServiceImpl implements PendingTaskService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public PendingTaskServiceImpl(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Long conversationId, PendingTask task) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || conversationId == null || task == null) {
            log.warn("保存 PendingTask 跳过：userId、conversationId 或 task 为空");
            return;
        }
        String key = buildKey(userId, conversationId);
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(task),
                    Duration.ofMinutes(PendingTaskConstant.TTL_MINUTES));
            log.info("保存 PendingTask key={}, taskType={}, status={}, ttlMinutes={}",
                    key, task.getTaskType(), task.getStatus(), PendingTaskConstant.TTL_MINUTES);
        } catch (JsonProcessingException e) {
            log.error("PendingTask 序列化失败, conversationId={}", conversationId, e);
        }
    }

    @Override
    public PendingTask get(Long conversationId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || conversationId == null) {
            return null;
        }
        String key = buildKey(userId, conversationId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PendingTask.class);
        } catch (Exception e) {
            log.warn("PendingTask 反序列化失败，删除损坏数据, key={}", key, e);
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    @Override
    public void delete(Long conversationId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || conversationId == null) {
            return;
        }
        String key = buildKey(userId, conversationId);
        Boolean removed = stringRedisTemplate.delete(key);
        if (Boolean.TRUE.equals(removed)) {
            log.info("删除 PendingTask key={}", key);
        }
    }

    private String buildKey(Long userId, Long conversationId) {
        return PendingTaskConstant.REDIS_KEY_PREFIX + userId + ":" + conversationId;
    }
}
