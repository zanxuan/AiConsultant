package com.zx.consultant.rag.eval;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 同时读取旧 golden-set 和新测试集。
 * <p>
 * 不用 {@code @JsonAlias}：{@code test-001} 要同时得到内部整数和原始字符串，
 * 而且已有 {@code query} 时不能被 {@code question} 覆盖。未知字段直接跳过。
 */
public class EvalCaseDeserializer extends JsonDeserializer<EvalCase> {

    /** 只接受 test- 后接十进制数字，不接受空格、符号或其他前缀 */
    private static final Pattern TEST_CASE_ID = Pattern.compile("^test-(\\d+)$");

    @Override
    public EvalCase deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw JsonMappingException.from(parser, "评测用例必须是 JSON 对象");
        }

        JsonNode idNode = node.get("id");
        EvalCase evalCase = new EvalCase();
        evalCase.setId(readId(parser, idNode));
        evalCase.setSourceCaseId(readSourceCaseId(parser, node, idNode));
        evalCase.setQuery(readQuery(parser, node));
        evalCase.setExpectedDocIds(readStringList(parser, node.get("expectedDocIds"), "expectedDocIds"));
        evalCase.setGroundTruthChunkIds(
                readStringList(parser, node.get("groundTruthChunkIds"), "groundTruthChunkIds"));
        return evalCase;
    }

    /**
     * 数字 ID 原样转为 Integer。test-数字 只取数值部分。
     * 缺失时保持 null，与旧解析一致；出现了但格式不对则直接失败。
     */
    private static Integer readId(JsonParser parser, JsonNode idNode) throws JsonMappingException {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isTextual()) {
            String raw = idNode.asText();
            Matcher matcher = TEST_CASE_ID.matcher(raw);
            if (!matcher.matches()) {
                throw JsonMappingException.from(parser,
                        "用例 ID 格式非法: \"" + raw + "\"，仅支持整数或 test-数字");
            }
            return toInteger(parser, matcher.group(1), raw);
        }
        if (idNode.isIntegralNumber()) {
            return toInteger(parser, idNode.bigIntegerValue(), idNode.asText());
        }
        throw JsonMappingException.from(parser,
                "用例 ID 格式非法: \"" + idNode.asText() + "\"，仅支持整数或 test-数字");
    }

    /**
     * 文本形式的 test-数字 以原始字符串为准。
     * 整数 ID 再读取显式 sourceCaseId，供已经落过快照的记录读回。
     */
    private static String readSourceCaseId(JsonParser parser, JsonNode node, JsonNode idNode)
            throws JsonMappingException {
        if (idNode != null && !idNode.isNull() && idNode.isTextual()) {
            String raw = idNode.asText();
            JsonNode explicit = node.get("sourceCaseId");
            if (explicit != null && !explicit.isNull()
                    && (!explicit.isTextual() || !raw.equals(explicit.asText()))) {
                throw JsonMappingException.from(parser,
                        "用例 ID 与 sourceCaseId 不一致: \"" + raw + "\"");
            }
            return raw;
        }

        JsonNode explicit = node.get("sourceCaseId");
        if (explicit == null || explicit.isNull()) {
            return null;
        }
        if (!explicit.isTextual()) {
            throw JsonMappingException.from(parser, "sourceCaseId 必须是字符串");
        }
        return explicit.asText();
    }

    /** query 有值时保留；只有新测试集的 question 才填入 query */
    private static String readQuery(JsonParser parser, JsonNode node) throws JsonMappingException {
        JsonNode query = node.get("query");
        if (query != null && !query.isNull()) {
            return requireText(parser, query, "query");
        }
        JsonNode question = node.get("question");
        if (question != null && !question.isNull()) {
            return requireText(parser, question, "question");
        }
        return null;
    }

    /**
     * 缺失或 null 保持 null，空数组保持空列表。
     * 元素必须本来就是字符串，避免雪花 ID 经过数字类型后丢精度。
     */
    private static List<String> readStringList(JsonParser parser, JsonNode node, String fieldName)
            throws JsonMappingException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw JsonMappingException.from(parser, fieldName + " 必须是字符串数组");
        }
        List<String> values = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            if (item == null || item.isNull() || !item.isTextual()) {
                throw JsonMappingException.from(parser,
                        fieldName + "[" + i + "] 必须是字符串，不能使用数字或空值，避免 ID 精度丢失");
            }
            values.add(item.asText());
        }
        return values;
    }

    private static String requireText(JsonParser parser, JsonNode node, String fieldName)
            throws JsonMappingException {
        if (!node.isTextual()) {
            throw JsonMappingException.from(parser, fieldName + " 必须是字符串");
        }
        return node.asText();
    }

    private static Integer toInteger(JsonParser parser, String digits, String raw) throws JsonMappingException {
        try {
            return toInteger(parser, new BigInteger(digits), raw);
        } catch (NumberFormatException e) {
            throw JsonMappingException.from(parser, "用例 ID 格式非法: \"" + raw + "\"，仅支持整数或 test-数字");
        }
    }

    /** 用 BigInteger 判断范围，避免先转 Long 或 Double 时溢出、丢精度 */
    private static Integer toInteger(JsonParser parser, BigInteger value, String raw) throws JsonMappingException {
        if (value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw JsonMappingException.from(parser, "用例 ID 超出 Integer 范围: \"" + raw + "\"");
        }
        return value.intValue();
    }
}
