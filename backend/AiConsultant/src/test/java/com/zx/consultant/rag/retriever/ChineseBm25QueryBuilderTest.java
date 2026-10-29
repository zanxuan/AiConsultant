package com.zx.consultant.rag.retriever;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ChineseBm25QueryBuilderTest {

    @Test
    void questionWordsStayOptionalAfterPunctuationSplit() {
        String query = ChineseBm25QueryBuilder.build("什么是缓存击穿、缓存穿透和缓存雪崩？");

        assertEquals("~什么是 缓存击穿 缓存穿透 ~和 缓存雪崩", query);
        assertFalse(query.contains("\\"));
    }

    @Test
    void plusAndHyphenAreBoundaries() {
        assertEquals(
                "~为什么 给常用查询字段建B 树能加快查找",
                ChineseBm25QueryBuilder.build("为什么给常用查询字段建B+树能加快查找？"));
        assertEquals("F 08", ChineseBm25QueryBuilder.build("F-08"));
    }

    @Test
    void topicWordsStayRequiredAndQuestionWordsAreOptional() {
        assertEquals("Redis ~有哪些 持久化方式", ChineseBm25QueryBuilder.build("Redis有哪些持久化方式？"));
        assertEquals(
                "一条select语句从客户端发出后 经历了 ~哪些 内部处理步骤",
                ChineseBm25QueryBuilder.build("一条select语句从客户端发出后，经历了哪些内部处理步骤？"));
    }

    @Test
    void doesNotCarveSingleCharacterOutOfLongerWords() {
        assertEquals("所有节点没有数据", ChineseBm25QueryBuilder.build("所有节点没有数据"));
        assertEquals("有效 ~的 方式 ~是什么", ChineseBm25QueryBuilder.build("有效的方式是什么？"));
    }

    @Test
    void keepsLatinWordsSeparateFromOptionalFunctionWords() {
        assertEquals("~什么是 MySQL ~的 MVCC", ChineseBm25QueryBuilder.build("什么是MySQL的MVCC？"));
        assertEquals("Spring IOC ~是什么", ChineseBm25QueryBuilder.build("Spring IOC是什么？"));
    }

    @Test
    void explainTermsBecomeOrQuery() {
        String explained = """
                INTERSECT {
                  同(expanded)
                  一类(expanded)
                  故障(expanded)
                  @text:故障(expanded)
                }
                """;

        assertEquals("同 | 一类 | 故障", ChineseBm25QueryBuilder.toOrQuery(explained));
        assertEquals("", ChineseBm25QueryBuilder.toOrQuery(" "));
        assertEquals("", ChineseBm25QueryBuilder.toOrQuery(null));
    }

    @Test
    void blankQueryBecomesEmpty() {
        assertEquals("", ChineseBm25QueryBuilder.build("   "));
        assertEquals("", ChineseBm25QueryBuilder.build(null));
        assertEquals("", ChineseBm25QueryBuilder.build("是什么？"));
    }
}
