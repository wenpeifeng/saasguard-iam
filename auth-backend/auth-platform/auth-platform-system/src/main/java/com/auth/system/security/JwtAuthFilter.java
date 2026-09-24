package com.auth.system.security;

import com.auth.framework.context.TenantContextHolder;
import com.auth.framework.log.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（认证中心管理端登录态）。
 * <p>
 * 每个请求执行一次：从 Authorization: Bearer {token} 解析用户，
 * 令牌综合有效（签名 + Redis 在线态 + 黑名单）时把认证信息放入 SecurityContext，
 * 并加载租户上下文与平台角色（ROLE_ 前缀 authorities，供 @PreAuthorize 判定）。
 * <p>
 * 约定：principal 直接放用户 ID（Long）。auth-framework 的审计填充器
 * 以 {@code instanceof Long} 读取，避免 framework 反向依赖 system —— 改动
 * principal 类型前必须同步评估两侧。
 * <p>
 * Bean 名固定为 {@code jwtAuthFilter}，auth-framework 的 SecurityConfig
 * 按名查找挂载到过滤链（避免反向编译依赖）。
 */
@Component("jwtAuthFilter")
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final SysRoleService roleService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        try {
            // 已有认证（理论上不会出现，防御性判断）或无令牌时不处理，交给授权规则判定
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (tokenProvider.isValid(token)) {
                    Long userId = tokenProvider.getUserId(token);
                    Long tenantId = tokenProvider.getTenantId(token);
                    String username = tokenProvider.getUsername(token);

                    // 先落租户上下文，后续角色查询即被租户插件按正确租户过滤 —— 顺序不可颠倒
                    TenantContextHolder.set(new TenantContextHolder.TenantContext(tenantId));
                    // 日志 MDC：租户/用户/用户名（traceId 由 TraceIdFilter 自管）
                    MDC.put(MdcKeys.TENANT_ID, String.valueOf(tenantId));
                    MDC.put(MdcKeys.USER_ID, String.valueOf(userId));
                    if (username != null) {
                        MDC.put(MdcKeys.USERNAME, username);
                    }

                    List<SimpleGrantedAuthority> authorities = roleService.getRoleCodesByUserId(userId).stream()
                            .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                            .toList();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
                // 令牌存在但无效：不设置认证，SecurityConfig 的 401 入口统一返回 Result JSON
            }
            filterChain.doFilter(request, response);
        } finally {
            // 无论请求成败必须清理，防止容器线程复用导致租户上下文串数据
            TenantContextHolder.clear();
            // 只移除本过滤器写入的键（traceId 由 TraceIdFilter 自管，不可 MDC.clear 误清）
            MDC.remove(MdcKeys.TENANT_ID);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.USERNAME);
        }
    }

    /**
     * 从请求头解析 Bearer 令牌。
     *
     * @param request 当前请求
     * @return 令牌字符串；头缺失或格式不符返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
