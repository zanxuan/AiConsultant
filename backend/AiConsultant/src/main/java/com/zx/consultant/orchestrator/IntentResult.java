package com.zx.consultant.orchestrator;

import com.zx.consultant.orchestrator.enums.Intent;
import com.zx.consultant.orchestrator.enums.SummaryScope;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {
    private Intent intent;
    private SummaryScope summaryScope;
}
