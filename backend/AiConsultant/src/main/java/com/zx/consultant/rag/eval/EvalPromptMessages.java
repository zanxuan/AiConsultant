package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.List;
import com.zx.consultant.common.constant.MessageRole;
import com.zx.consultant.llm.entity.PromptRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * 把 {@link PromptRequest} 转成消息。
 * 拼接方式和 {@code LLMServiceImpl} 的无工具路径一致，但评测不调用 {@code generateAnswer}。
 */
final class EvalPromptMessages {

    private EvalPromptMessages() {
    }

    static List<ChatMessage> toChatMessages(PromptRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(SystemMessage.from(request.getSystemPrompt()));
        }
        if (request.getHistory() != null) {
            for (var msg : request.getHistory()) {
                if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
                    continue;
                }
                if (MessageRole.SYSTEM.equalsIgnoreCase(msg.getRole())) {
                    messages.add(SystemMessage.from(msg.getContent()));
                } else if (MessageRole.USER.equalsIgnoreCase(msg.getRole())) {
                    messages.add(UserMessage.from(msg.getContent()));
                } else if (MessageRole.ASSISTANT.equalsIgnoreCase(msg.getRole())) {
                    messages.add(AiMessage.from(msg.getContent()));
                }
            }
        }
        String context = request.getContext() == null ? "" : request.getContext();
        String query = request.getUserQuery() == null ? "" : request.getUserQuery();
        messages.add(UserMessage.from(context + "\n\n【用户问题】:\n" + query));
        return messages;
    }
}
