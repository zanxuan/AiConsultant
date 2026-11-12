package com.zx.consultant.rag.eval;

import java.time.Duration;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

/**
 * 给 DeepSeek 请求补上 {@code thinking.type=disabled}。
 * 思考模式打开时温度不生效，裁判分数会跟着推理波动。
 */
final class ThinkingDisabledHttpClient implements HttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient delegate;

    ThinkingDisabledHttpClient(HttpClient delegate) {
        this.delegate = delegate;
    }

    /** 非 JSON 正文原样返回，避免拦截器把正常请求改坏 */
    static String withThinkingDisabled(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            if (!(root instanceof ObjectNode object)) {
                return body;
            }
            object.putObject("thinking").put("type", "disabled");
            return MAPPER.writeValueAsString(object);
        } catch (JacksonException e) {
            return body;
        }
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        return delegate.execute(rewrite(request));
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        delegate.execute(rewrite(request), parser, listener);
    }

    private static HttpRequest rewrite(HttpRequest request) {
        String body = withThinkingDisabled(request.body());
        if (body == null || body.equals(request.body())) {
            return request;
        }
        return HttpRequest.builder()
                .method(request.method())
                .url(request.url())
                .headers(request.headers())
                .body(body)
                .build();
    }

    /** 超时仍交给原来的 HTTP 客户端，只包一层改写正文 */
    static final class Builder implements HttpClientBuilder {

        private final HttpClientBuilder delegate;

        Builder() {
            this(HttpClientBuilderLoader.loadHttpClientBuilder());
        }

        Builder(HttpClientBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public Duration connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration timeout) {
            delegate.connectTimeout(timeout);
            return this;
        }

        @Override
        public Duration readTimeout() {
            return delegate.readTimeout();
        }

        @Override
        public HttpClientBuilder readTimeout(Duration timeout) {
            delegate.readTimeout(timeout);
            return this;
        }

        @Override
        public HttpClient build() {
            return new ThinkingDisabledHttpClient(delegate.build());
        }
    }
}
