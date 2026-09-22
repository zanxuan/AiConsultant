package com.zx.consultant.rag.eval;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 现有 golden-set 的只读视图，供 GET /eval/dataset 返回。
 */
@Data
public class EvalDataset {

    /** 实际读到的文件名，如 golden-set.json */
    private String name;

    private List<EvalCase> cases = new ArrayList<>();
}
