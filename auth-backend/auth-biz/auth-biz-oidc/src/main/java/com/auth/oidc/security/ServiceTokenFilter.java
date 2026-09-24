package com.auth.oidc.security;

import com.auth.oidc.service.OidcTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 服务间令牌过滤器 —— /service/** 的认证收口（client_credentials service token）。
 * <p>
 * 校验 Bearer RS256 令牌：token_use=service + 未拉黑，通过后写入
 * SecurityContext（principal=client_id 字符串，ROLE_SERVICE），
 * 供 ServiceApiController 做 @PreAuthorize 与 client_id 一致性校验。
 * 白名单放行 + 本过滤器自管认证（与 JwtAuthFilter 同样的模式）。
 */
@Component
@RequiredArgsConstructor
public class ServiceTokenFilter extends OncePerRequestFilter {

    private final OidcTokenService tokenService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只管 /service/**（URI 含 context-path /api，按段匹配）；OPTIONS 预检放行
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/service") || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokenService.parse(header.substring(7).trim());
                boolean usable = "service".equals(claims.get(OidcTokenService.CLAIM_TOKEN_USE))
                        && claims.getAudience() != null && !claims.getAudience().isEmpty()
                        && !tokenService.isRevoked(claims.getId());
                if (usable) {
                    String clientId = claims.getAudience().iterator().next();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(clientId, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // 解析失败保持未认证，授权规则返回 401
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
