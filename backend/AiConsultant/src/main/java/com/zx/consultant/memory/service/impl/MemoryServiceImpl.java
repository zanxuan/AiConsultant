package com.zx.consultant.memory.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.common.constant.MemoryConstant;
import com.zx.consultant.common.constant.MessageRole;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.memory.model.MemoryItem;
import com.zx.consultant.memory.model.MemoryRound;
import com.zx.consultant.memory.model.ParsedMemory;
import com.zx.consultant.memory.service.MemoryService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 短期记忆：List 存摘要 + 对话轮次，超限时「旧摘要 + 前半窗口」压缩为新摘要，只保留后半窗口。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    /** 复用问答同款 ChatModel 生成摘要，TODO 后期可换专用摘要模型 */
    private final ChatModel chatModel;

    /**
     * 加载最近的对话消息
     * @param conversationId 会话ID
     * @param limit 限制返回的消息数量
     * @return 消息列表
     */
    @Override
    public List<Message> getRecentMessages(Long conversationId, int limit) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || conversationId == null) {
            log.warn("加载短期记忆失败：userId 或 conversationId 为空");
            return Collections.emptyList();
        }

        String key = buildKey(userId, conversationId);
        ParsedMemory parsed = loadParsed(key);
        if (parsed.getSummary() == null && parsed.getRounds().isEmpty()) {
            log.info("Redis 短期记忆为空 userId={}, sessionId={}", userId, conversationId);
            return Collections.emptyList();
        }

        // 将视图模型转换为消息列表
        List<Message> messages = toMessages(parsed);
        log.info(
            "加载 Redis 短期记忆 userId={}, sessionId={}, rounds={}, hasSummary={}, messageCount={}",
            userId,
            conversationId,
            parsed.roundCount(),
            parsed.getSummary() != null && !parsed.getSummary().isBlank(),
            messages.size()
        );
        return messages;
    }

    /**
     * 写入一轮对话
     * @param conversationId 会话ID
     * @param userQuestion 用户问题
     * @param aiAnswer AI回答
     */
    @Override
    public void appendTurn(Long conversationId, String userQuestion, String aiAnswer) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || conversationId == null) {
            log.warn("写入短期记忆跳过：userId 或 conversationId 为空");
            return;
        }
        if (userQuestion == null || userQuestion.isBlank()) {
            return;
        }

        String key = buildKey(userId, conversationId);
        // 创建一轮对话的内存项：判断AI回答是否为空，如果为空，则设置为空字符串
        MemoryItem roundItem = MemoryItem.round(userQuestion, aiAnswer);
        // 将一轮对话写入 Redis List：将一轮对话的内存项写入 Redis List
        stringRedisTemplate.opsForList().rightPush(key, serialize(roundItem));

        log.info("写入一轮短期记忆 userId={}, sessionId={}", userId, conversationId);

        // 如果需要压缩，则压缩
        if (needCompress(key)) {
            // 压缩
            compress(key);
        }
    }

    /**===============================私有方法===================================*/
    /**
     * 判断是否需要压缩
     * @param key Redis 键
     * @return 是否需要压缩
     */
    private boolean needCompress(String key) {
        ParsedMemory parsed = loadParsed(key);
        // 获取对话轮次
        int rounds = parsed.roundCount();
        // 获取总字符数
        int chars = parsed.totalChars();
        // 判断是否超过最大对话轮次
        boolean overRounds = rounds > MemoryConstant.MAX_MEMORY_ROUNDS;
        // 判断是否超过最大字符数
        boolean overChars = chars > MemoryConstant.MAX_MEMORY_CHARS;
        // 判断是否超过最大限制
        if (overRounds || overChars) {
            // 记录日志
            log.info(
                "触发短期记忆压缩 key={}, rounds={}, chars={}, overRounds={}, overChars={}",
                key,
                rounds,
                chars,
                overRounds,
                overChars
            );
            return true;
        }
        return false;
    }

    /**
     * 摘要策略：新摘要 = LLM(旧摘要 + 窗口前一半)；写回 = 新摘要 + 窗口后一半
     */
    private void compress(String key) {
        ParsedMemory parsed = loadParsed(key);
        List<MemoryRound> rounds = parsed.getRounds();
        if (rounds.isEmpty()) {
            return;
        }

        // 计算窗口中间位置
        int mid = Math.max(1, rounds.size() / 2);
        // 计算窗口前一半,生成全新List，切断和原集合关联
        List<MemoryRound> firstHalf = new ArrayList<>(rounds.subList(0, mid));
        // 计算窗口后一半
        List<MemoryRound> secondHalf = new ArrayList<>(rounds.subList(mid, rounds.size()));

        log.info(
            "开始短期记忆压缩 key={}, totalRounds={}, mid={}, summarizeRounds={}, keepRounds={}, hasOldSummary={}",
            key,
            rounds.size(),
            mid,
            firstHalf.size(),
            secondHalf.size(),
            parsed.getSummary() != null && !parsed.getSummary().isBlank()
        );

        String newSummary = generateSummary(parsed.getSummary(), firstHalf);
        log.info(
            "短期记忆摘要生成完成 key={}, summaryLen={}, preview={}",
            key,
            newSummary == null ? 0 : newSummary.length(),
            preview(newSummary, 120)
        );

        // 重写 Redis：删除 Redis List，创建要推送的元素列表，将新摘要和对话轮次写入 Redis List
        rewriteRedis(key, newSummary, secondHalf);

        // 若仍超限则继续压缩，设上限防止异常循环
        int guard = 0;
        while (needCompress(key) && guard++ < 5) {
            ParsedMemory again = loadParsed(key);
            if (again.getRounds().isEmpty()) {
                break;
            }
            int m = Math.max(1, again.roundCount() / 2);
            List<MemoryRound> fh = new ArrayList<>(again.getRounds().subList(0, m));
            List<MemoryRound> sh = new ArrayList<>(again.getRounds().subList(m, again.roundCount()));
            log.info(
                "短期记忆继续压缩 pass={}, key={}, totalRounds={}, mid={}, summarizeRounds={}, keepRounds={}",
                guard,
                key,
                again.roundCount(),
                m,
                fh.size(),
                sh.size()
            );
            String againSummary = generateSummary(again.getSummary(), fh);
            log.info(
                "短期记忆摘要生成完成 pass={}, key={}, summaryLen={}, preview={}",
                guard,
                key,
                againSummary == null ? 0 : againSummary.length(),
                preview(againSummary, 120)
            );
            rewriteRedis(key, againSummary, sh);
        }
    }

    /**
     * 重写 Redis
     * @param key Redis 键
     * @param summary 摘要
     * @param rounds 对话轮次
     */
    private void rewriteRedis(String key, String summary, List<MemoryRound> rounds) {
        // 删除 Redis List
        stringRedisTemplate.delete(key);
        // 创建要推送的元素列表
        List<String> toPush = new ArrayList<>();

        if (summary != null && !summary.isBlank()) {
            toPush.add(serialize(MemoryItem.summary(summary)));
        }
        for (MemoryRound round : rounds) {
            toPush.add(serialize(MemoryItem.round(round.getUser(), round.getAssistant())));
        }
        if (!toPush.isEmpty()) {
            stringRedisTemplate.opsForList().rightPushAll(key, toPush);
        }
        log.info(
            "短期记忆已写回 Redis key={}, summaryLen={}, remainRounds={}",
            key,
            summary == null ? 0 : summary.length(),
            rounds.size()
        );
    }

    /**
     * 生成摘要
     * @param oldSummary 旧摘要
     * @param roundsToSummarize 需要并入摘要的对话轮次
     * @return 摘要
     */
    private String generateSummary(String oldSummary, List<MemoryRound> roundsToSummarize) {
        // 创建对话内容
        StringBuilder dialogue = new StringBuilder();
        // 遍历需要并入摘要的对话轮次
        for (MemoryRound round : roundsToSummarize) {
            // 添加用户问题
            dialogue.append("用户: ").append(nullToEmpty(round.getUser())).append("\n");
            // 添加AI回答
            dialogue.append("助手: ").append(nullToEmpty(round.getAssistant())).append("\n");
        }

        String prompt = """
                你是短期记忆摘要模块。
                目标：
                压缩历史对话，帮助后续回答保持上下文。请将【已有摘要】 【需要并入摘要的对话】压缩成一段简洁、完整的中文摘要。不要重复已有摘要中的内容。
                如果已有摘要已经包含某个事实，不要展开描述。不要回答问题，只输出摘要正文。

                要求：
                - 最大%d字
                - 保留：
                1. 用户长期目标
                2. 已完成工作
                3. 技术选型
                4. 当前问题
                5. 未解决事项


                【已有摘要】
                %s

                【需要并入摘要的对话】
                %s

                【新摘要】
                """.formatted(
                MemoryConstant.MAX_MEMORY_SUMMARY_LENGTH,
                (oldSummary == null || oldSummary.isBlank()) ? "（无）" : oldSummary,
                dialogue.toString().trim()
        );

        try {
            String summary = chatModel.chat(prompt);
            return normalizeSummary(summary);
        } catch (Exception e) {
            log.error("生成短期记忆摘要失败，降级拼接原文截断", e);
            String fallback = (oldSummary == null ? "" : oldSummary + "\n") + dialogue;
            return normalizeSummary(fallback);
        }
    }

    /**
     * 校验摘要长度；超出上限则再调用一次 LLM 压缩到限定字数内。
     */
    private String normalizeSummary(String summary) {
        if (summary == null) {
            return "";
        }
        summary = summary.trim();
        if (summary.isEmpty() || summary.length() <= MemoryConstant.MAX_MEMORY_SUMMARY_LENGTH) {
            return summary;
        }

        log.info(
            "摘要超长，触发二次压缩 length={}, limit={}",
            summary.length(),
            MemoryConstant.MAX_MEMORY_SUMMARY_LENGTH
        );
        String recompressed = compressAgain(summary);
        if (recompressed.length() <= MemoryConstant.MAX_MEMORY_SUMMARY_LENGTH) {
            return recompressed;
        }

        // 二次压缩仍超限时硬截断，避免 Redis 摘要失控膨胀
        log.warn(
            "二次压缩后仍超长，硬截断 length={}, limit={}",
            recompressed.length(),
            MemoryConstant.MAX_MEMORY_SUMMARY_LENGTH
        );
        return recompressed.substring(0, MemoryConstant.MAX_MEMORY_SUMMARY_LENGTH);
    }

    /**
     * 对已生成摘要再压缩一次
     */
    private String compressAgain(String summary) {
        String prompt = """
                请压缩到%d字以内。
                保留关键事实、用户目标、技术选型和未解决问题，删除重复与细节。
                只输出摘要正文，不要解释。

                【待压缩摘要】
                %s

                【压缩后摘要】
                """.formatted(MemoryConstant.MAX_MEMORY_SUMMARY_LENGTH, summary);

        try {
            String result = chatModel.chat(prompt);
            return result == null ? summary : result.trim();
        } catch (Exception e) {
            log.error("二次压缩摘要失败，保留原摘要待硬截断", e);
            return summary;
        }
    }

    /**
     * 从 Redis List 解析为视图模型
     * @param key Redis 键
     * @return 视图模型
     */
    private ParsedMemory loadParsed(String key) {
        // 获取 Redis List 中的所有元素
        List<String> rawItems = stringRedisTemplate.opsForList().range(key, 0, -1);
        // 创建视图模型
        ParsedMemory parsed = new ParsedMemory();
        // 如果 Redis List 为空，则返回空视图模型
        if (rawItems == null || rawItems.isEmpty()) {
            return parsed;
        }
        // 遍历 Redis List 中的所有元素
        for (String raw : rawItems) {
            // 反序列化元素
            MemoryItem item = deserialize(raw);
            // 如果元素为空，则跳过
            if (item == null) {
                continue;
            }
            // 如果元素为摘要，则设置摘要
            if (item.isSummary()) {
                parsed.setSummary(item.getContent());
            } else if (item.isRound()) {
                // 如果元素为一轮对话，则添加到视图模型中
                parsed.getRounds().add(new MemoryRound(item.getUser(), item.getAssistant()));
            }
        }
        return parsed;
    }

    /**
     * 将视图模型转换为消息列表
     * @param parsed 视图模型
     * @return 消息列表
     */
    private List<Message> toMessages(ParsedMemory parsed) {
        // 创建消息列表
        List<Message> messages = new ArrayList<>();
        String summary = parsed.getSummary();
        // 如果摘要不为空，则添加到消息列表中
        if (summary != null && !summary.isBlank()) {
            Message summaryMsg = new Message();
            // 摘要是上下文信息，不是助手回复，必须用 system，避免模型误当成历史 AI 发言
            summaryMsg.setRole(MessageRole.SYSTEM);
            summaryMsg.setContent("以下是历史对话摘要：\n" + summary);
            messages.add(summaryMsg);
        }
        // 遍历视图模型中的所有对话轮次
        for (MemoryRound round : parsed.getRounds()) {
            // 如果用户问题不为空，则添加到消息列表中
            if (round.getUser() != null) {
                Message userMsg = new Message();
                userMsg.setRole(MessageRole.USER);
                userMsg.setContent(round.getUser());
                // 添加到消息列表中
                messages.add(userMsg);
            }
            if (round.getAssistant() != null) {
                Message aiMsg = new Message();
                aiMsg.setRole(MessageRole.ASSISTANT);
                aiMsg.setContent(round.getAssistant());
                messages.add(aiMsg);
            }
        }
        return messages;
    }

    private String buildKey(Long userId, Long sessionId) {
        return MemoryConstant.REDIS_KEY_PREFIX + userId + ":" + sessionId;
    }

    private String serialize(MemoryItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("短期记忆序列化失败", e);
        }
    }

    private MemoryItem deserialize(String raw) {
        try {
            // 反序列化元素：：JSON 字符串 → Java 实体类
            return objectMapper.readValue(raw, MemoryItem.class);
        } catch (Exception e) {
            // 如果反序列化失败，则跳过
            log.warn("短期记忆反序列化失败，已跳过: {}", raw, e);
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 日志预览截断，避免把整段摘要打爆终端 */
    private static String preview(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "...";
    }
}
