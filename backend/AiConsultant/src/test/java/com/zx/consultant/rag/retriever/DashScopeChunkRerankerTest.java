package com.zx.consultant.rag.retriever;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 只检查通义返回如何还原成入参下标上的分数，不发请求。
 */
class DashScopeChunkRerankerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void scoresStayOnTheOriginalIndex() throws Exception {
        String json = """
                {"output":{"results":[
                  {"index":1,"relevance_score":0.9},
                  {"index":0,"relevance_score":0.2}
                ]}}
                """;

        double[] scores = DashScopeChunkReranker.toScores(objectMapper.readTree(json), 2);

        assertArrayEquals(new double[] {0.2d, 0.9d}, scores);
    }

    @Test
    void incompleteResultsAreRejected() throws Exception {
        String json = """
                {"output":{"results":[{"index":0,"relevance_score":0.9}]}}
                """;

        assertNull(DashScopeChunkReranker.toScores(objectMapper.readTree(json), 2));
    }

    @Test
    void duplicateIndexIsRejected() throws Exception {
        String json = """
                {"output":{"results":[
                  {"index":0,"relevance_score":0.9},
                  {"index":0,"relevance_score":0.1}
                ]}}
                """;

        assertNull(DashScopeChunkReranker.toScores(objectMapper.readTree(json), 2));
    }

    @Test
    void errorBodyKeepsCodeAndMessage() throws Exception {
        String json = """
                {"code":"InvalidApiKey","message":"bad key","request_id":"abc"}
                """;

        assertEquals("InvalidApiKey: bad key", DashScopeChunkReranker.errorMessage(objectMapper.readTree(json)));
    }
}
