package com.zx.consultant.rag.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.zx.consultant.rag.service.RewriteService;
import com.zx.consultant.chat.entity.Message;
import com.zx.consultant.common.constant.MessageRole;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;

@Slf4j
@Service
public class RewriteServiceImpl implements RewriteService {

    private final ChatModel chatModel;
   
    public RewriteServiceImpl(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String rewriteQuery(String originalQuery, List<Message> memory) {
        // 1. 如果没有历史记忆，直接返回原问题
        if (memory == null || memory.isEmpty()) {
            return originalQuery;
        }

        // 2. 拼接历史：system 摘要作为上下文，user/assistant 为真实对话轮次
        StringBuilder historyBuilder = new StringBuilder();
        for (Message msg : memory) {
            if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            if (MessageRole.SYSTEM.equalsIgnoreCase(msg.getRole())) {
                historyBuilder.append(msg.getContent()).append("\n");
            } else if (MessageRole.USER.equalsIgnoreCase(msg.getRole())) {
                historyBuilder.append("用户: ").append(msg.getContent()).append("\n");
            } else if (MessageRole.ASSISTANT.equalsIgnoreCase(msg.getRole())) {
                historyBuilder.append("助手: ").append(msg.getContent()).append("\n");
            }
        }
        String history = historyBuilder.toString().trim();
        if (history.isEmpty()) {
            return originalQuery;
        }

        // 3. 使用 Java 17 文本块设计 Prompt
        String template = """
                你是一个企业知识库查询优化助手。
                
                你的任务：
                根据历史对话，将用户当前问题改写成一个适合知识库检索的完整问题。
                
                要求：
                1. 保留用户真实意图
                2. 补充必要上下文
                3. 删除指代词（例如：它、这个、那个），替换为具体的实体名词
                4. 输出一个独立、语义完整的检索问题
                5. 绝对不要回答该问题！只输出改写后的文本！
                6. 如果当前问题已经很完整且与历史无关，请直接输出原问题。
                
                历史对话:
                {{history}}
                
                当前问题:
                {{query}}
                
                改写后的检索问题:
                """;

        PromptTemplate promptTemplate = PromptTemplate.from(template);

        // 4. 填充变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("history", history);
        variables.put("query", originalQuery);
        Prompt prompt = promptTemplate.apply(variables);

        // 5. 调用大模型生成，并实现优雅降级 (Graceful Degradation)
        try {
            return chatModel.chat(prompt.text()).trim();
        } catch (Exception e) {
            log.warn("Query Rewrite 失败，触发优雅降级，返回原问题。失败原因: {}", e.getMessage());
            return originalQuery;
        }
    }
}