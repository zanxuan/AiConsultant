package com.zx.consultant.common.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 公共配置
 * CommonConfig
 */
@Configuration
public class CommonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private Integer redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Redis 会话记忆存储（langchain4j 1.0.1-beta6 使用 host/port 构建）
     */
    @Bean
    public ChatMemoryStore redisChatMemoryStore() {
        return RedisChatMemoryStore.builder()
                .host(redisHost)
                .port(redisPort)
                .password(redisPassword.isEmpty() ? null : redisPassword)
                .prefix("chat_memory:")
                .build();
    }

    /**
     * 聊天记忆
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    /**
     * 聊天记忆提供者（按 memoryId 隔离会话）
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore redisChatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }
}
