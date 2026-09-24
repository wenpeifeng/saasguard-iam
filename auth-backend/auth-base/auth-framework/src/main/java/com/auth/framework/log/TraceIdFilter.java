package com.auth.framework.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路 ID 过滤器 —— 每个请求分配 traceId 写入 MDC，并在响应头 X-Trace-Id 回显。
 * <p>
 * 顺序约束：必须在整条过滤链最先执行（{@code Ordered.HIGHEST_PRECEDENCE}，见
 * {@code WebConfig#traceIdFilterRegistration}），早于 Spring Security 过滤链
 * （含 JwtAuthFilter），这样 401/403 拒绝日志、JWT 解析日志同样带 traceId。
 * <p>
 * 规则：
 * <ul>
 *   <li>请求头携带合法 X-Trace-Id（8~64 位字母数字 -_）时沿用（网关/前端/上游服务透传），
 *       否则生成 16 位十六进制新 ID；不合法值一律丢弃重生成，防止日志注入；</li>
 *   <li>响应头在放行前写入（响应提交后无法再设头），业务异常/401 响应亦携带；</li>
 *   <li>白名单（探活/健康检查/API 文档静态资源）跳过：高频且无业务意义，不占 MDC 也不刷日志；</li>
 *   <li>finally 只移除 traceId 一个键（不 MDC.clear），避免误清其他过滤器管理的上下文键。</li>
 * </ul>
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /** 合法的外部 traceId：防止把任意字符串（含换行、JSON 引号）带进日志 */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9\\-_]{8,64}$");

    /** 跳过路径（相对 context-path）：探活 / 健康检查 / API 文档 —— 与 SecurityConfig 白名单中的基础设施项对齐 */
    private static final List<String> SKIP_PATTERNS = List.of(
            "/ping", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return SKIP_PATTERNS.stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(MdcKeys.TRACE_ID, traceId);
        response.setHeader(MdcKeys.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.TRACE_ID);
        }
    }

    /** 沿用合法的上游 traceId，否则生成新 ID */
    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(MdcKeys.TRACE_ID_HEADER);
        if (incoming != null && SAFE_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return newTraceId();
    }

    /**
     * 生成 16 位十六进制 traceId（UUID 高 64 位）：足够区分单机日均亿级请求，
     * 比完整 UUID 短一半，便于人工复制检索。
     */
    public static String newTraceId() {
        long hi = UUID.randomUUID().getMostSignificantBits();
        return String.format("%016x", hi);
    }
}
