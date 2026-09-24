package com.auth.system.audit;

import com.auth.framework.log.MdcKeys;
import com.auth.system.entity.AuditOpLog;
import com.auth.system.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 操作日志过滤器 —— 管理端写操作（POST/PUT/DELETE）统一切面落库。
 * <p>
 * 行为约定：
 * <ul>
 *   <li>只审计管理端写操作；登录/登出（/auth/login、/auth/logout）由登录日志覆盖；
 *       OIDC 协议端点（/oauth/**）与开放探活不审计；</li>
 *   <li>操作人取 SecurityContext（JWT 过滤器已写入 principal=用户 ID）+ MDC 用户名；</li>
 *   <li>请求体摘要脱敏后截断 512 字符（口令/密钥字段一律置 ***）；</li>
 *   <li>落库异步（AuditService @Async），审计失败不影响主流程。</li>
 * </ul>
 * 请求体捕获：本过滤器把请求包装为 ContentCachingRequestWrapper，链路执行完后
 * 缓存区才有内容，因此在 doFilter 之后读取。
 */
@Component
public class OpLogFilter extends OncePerRequestFilter {

    /** 不审计的路径前缀（登录日志/协议端点/探活已各自有日志体系） */
    private static final Set<String> EXCLUDED_PREFIXES = Set.of("/auth/login", "/auth/logout", "/oauth/", "/ping", "/error");

    /** 请求体摘要中的敏感字段（值替换为 ***） */
    private static final Set<String> SENSITIVE_FIELDS = Set.of("password", "clientSecret", "oldPassword", "newPassword", "secret");

    /** 摘要最大长度（与表列宽一致） */
    private static final int DETAIL_MAX = 512;

    private final AuditService auditService;

    public OpLogFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        boolean mutating = HttpMethod.POST.matches(method) || HttpMethod.PUT.matches(method) || HttpMethod.DELETE.matches(method);
        if (!mutating) {
            return true;
        }
        String uri = request.getRequestURI();
        return EXCLUDED_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            // 异常兜底：抛出的异常由全局异常处理器转 200/4xx，走到这里的都是已应答或即将由容器应答的请求
            record(request, wrapped, response, System.currentTimeMillis() - start);
        }
    }

    /** 组装并异步落库操作日志 */
    private void record(HttpServletRequest request, ContentCachingRequestWrapper wrapped,
                        HttpServletResponse response, long costMs) {
        try {
            AuditOpLog entry = new AuditOpLog();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Long userId) {
                entry.setUserId(userId);
            }
            String mdcUser = MDC.get(MdcKeys.USERNAME);
            if (mdcUser != null) {
                entry.setUsername(mdcUser);
            }
            entry.setMethod(request.getMethod());
            entry.setUri(request.getRequestURI());
            entry.setStatusCode(response.getStatus());
            entry.setDetail(summarize(wrapped));
            entry.setIp(clientIp(request));
            entry.setCostMs(costMs);
            entry.setCreateTime(LocalDateTime.now());
            auditService.recordOp(entry);
        } catch (Exception e) {
            logger.warn("[审计] 操作日志组装失败 uri=" + request.getRequestURI(), e);
        }
    }

    /** 请求体摘要：JSON 一行化 + 敏感字段脱敏 + 截断 */
    private String summarize(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        String body = new String(content, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        for (String field : SENSITIVE_FIELDS) {
            body = body.replaceAll("(\"%s\"\\s*:\\s*\")[^\"]*(\")".formatted(field), "$1***$2");
        }
        return body.length() > DETAIL_MAX ? body.substring(0, DETAIL_MAX) : body;
    }

    /** 客户端 IP（优先代理头） */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
