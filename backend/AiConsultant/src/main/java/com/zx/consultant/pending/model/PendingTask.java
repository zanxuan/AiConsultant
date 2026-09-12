package com.zx.consultant.pending.model;

import com.zx.consultant.pending.enums.PendingTaskStatus;
import com.zx.consultant.pending.enums.PendingTaskType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话上的短生命周期待处理任务。存 Redis，不代表后台线程在跑，也不改变 conversation 模式。
 */
@Data
public class PendingTask {

    /** 任务类型，例如 SUMMARY_DOCUMENT */
    private PendingTaskType taskType;

    /** 触发澄清的用户原始请求，恢复任务时必须保留，不能被后续文件名覆盖。 */
    private String originalQuery;

    /** 当前等待用户补充的信息，例如 WAITING_FOR_DOCUMENT */
    private PendingTaskStatus status;

    /** 澄清时展示过的候选文档名，用于判断下一轮是否在回答澄清。 */
    private List<String> candidateFileNames = new ArrayList<>();

    /** 创建任务时的知识库，恢复时用于校验会话仍绑定同一库。 */
    private Long knowledgeId;
}
