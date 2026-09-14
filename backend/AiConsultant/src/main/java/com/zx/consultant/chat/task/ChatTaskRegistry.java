package com.zx.consultant.chat.task;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 taskId 保存 ChatTask。第一阶段只做存在性判断和结果缓冲，不引入状态机。
 */
@Component
public class ChatTaskRegistry {

    private final ConcurrentHashMap<String, ChatTask> tasks = new ConcurrentHashMap<>();

    public ChatTask create(Long conversationId) {
        ChatTask task = new ChatTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setConversationId(conversationId);
        tasks.put(task.getTaskId(), task);
        return task;
    }

    public ChatTask get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return tasks.get(taskId);
    }

    public void remove(String taskId) {
        if (taskId != null) {
            tasks.remove(taskId);
        }
    }
}
