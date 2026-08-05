package com.zx.consultant.rag.eval;

import java.util.List;
import lombok.Data;

/**
 * golden-set 中的单条评测用例
 */
@Data
public class EvalCase {

    private Integer id;

    private String query;

    /** 期望命中的文档 ID（与 RetrievedChunk.documentId 字符串形式对齐） */
    private List<String> expectedDocIds;
}
