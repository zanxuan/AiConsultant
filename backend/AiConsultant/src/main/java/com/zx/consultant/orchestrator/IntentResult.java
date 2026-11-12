package com.zx.consultant.orchestrator;

import com.zx.consultant.orchestrator.enums.Intent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主意图结果。总结范围不在这里区分，进入 SUMMARY 后再选择工具。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {
    private Intent intent;
}
