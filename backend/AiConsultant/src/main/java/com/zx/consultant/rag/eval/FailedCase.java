package com.zx.consultant.rag.eval;

import java.util.List;
import lombok.Data;

@Data
public class FailedCase {

    private Integer id;

    private String query;

    private List<String> expectedDocIds;

    private List<String> retrievedDocIds;

    private Double topScore;
}
