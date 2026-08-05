package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class EvalResult {

    private int total;

    private double hitRate;

    private double recall;

    private double mrr;

    private List<FailedCase> failedCases = new ArrayList<>();
}
