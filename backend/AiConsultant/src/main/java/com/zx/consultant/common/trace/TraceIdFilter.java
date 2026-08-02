package com.zx.consultant.common.trace;

import com.zx.consultant.common.utils.BaseContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TraceId 过滤器：在请求入口生成/透传 traceId，写入 MDC，并回写响应头。
 * <p>
 * 执行顺序靠前，确保后续 JWT 拦截器、Controller、Workflow 的日志都能带上 traceId。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TraceConstants.HEADER_NAME);
        String traceId = TraceContext.init(incoming);
        // 尽早写入响应头，即使后续业务异常，客户端也能拿到 traceId
        response.setHeader(TraceConstants.HEADER_NAME, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理 Trace + 用户上下文，避免 Tomcat 线程池复用串扰
            TraceContext.clear();
            BaseContext.removeCurrentId();
        }
    }
}
