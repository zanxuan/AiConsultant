package com.zx.consultant;

import com.zx.consultant.llm.entity.PromptRequest;
import com.zx.consultant.llm.service.LLMService;
import com.zx.consultant.workflow.context.WorkflowContext;
import com.zx.consultant.workflow.service.WorkflowService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 测试类
 * LlmMainTest
 */
public class LlmMainTest {
    /**
     * 主方法
     * @param args
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ConsultantApplication.class, args);

        // 原有LLM普通问答测试
        LLMService llmService = context.getBean(LLMService.class);
        PromptRequest promptRequest = new PromptRequest();
        promptRequest.setUserQuery("什么是Spring Boot?");
        String result = llmService.generateAnswer(promptRequest);
        System.out.println("普通LLM回答：");
        System.out.println(result);

        // 新增原testRag的RAG流程测试逻辑
        WorkflowService workflowService = context.getBean(WorkflowService.class);
        WorkflowContext ragContext = new WorkflowContext();
        ragContext.setOriginalQuery("缓存击穿是什么？");
        WorkflowContext ragResult = workflowService.run(ragContext);
        System.out.println("\nRAG流程回答：");
        System.out.println(ragResult.getFinalAnswer());
    }
}