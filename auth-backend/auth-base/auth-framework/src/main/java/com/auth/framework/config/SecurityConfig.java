package com.auth.framework.config;

import com.auth.common.result.ErrorCode;
import com.auth.common.result.Result;
import com.auth.framework.i18n.AuthMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Spring Security 配置（JWT 无状态认证）。
 * <p>
 * 放行策略：登录 / 探活 / Swagger / 公开字典走白名单，其余接口一律需认证；
 * 未认证请求由 401 入口统一返回 {@code Result} JSON（code=401），前端据此跳登录页。
 * <p>
 * 过滤器接入方式：JWT 过滤器由 auth-platform-system 模块提供（Bean 名固定
 * {@code jwtAuthFilter}），此处按名查找挂到用户名密码过滤器之前 ——
 * 避免 framework 对 system 的反向编译依赖（依赖方向只能向下）。
 * 白名单新增必须同步评审 {@code JwtAuthFilter} 与前端 axios 拦截器行为。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 开启方法级权限：Controller 上用 @RequirePerm/@PreAuthorize 校验权限码
public class SecurityConfig {

    /** auth-platform-system 提供的 JWT 过滤器 Bean 名（模块间按名约定的唯一耦合点） */
    private static final String JWT_FILTER_BEAN_NAME = "jwtAuthFilter";

    /** auth-biz-oidc 提供的服务间令牌过滤器 Bean 名（/service/** 认证收口） */
    private static final String SERVICE_FILTER_BEAN_NAME = "serviceTokenFilter";

    /**
     * 方法注解模板支持（Security 6.3+）：使 @RequirePerm 元注解中的
     * {@code {value}} 占位符解析为自定义注解属性值。缺此 Bean 自定义权限注解不生效。
     */
    @Bean
    static PrePostTemplateDefaults prePostTemplateDefaults() {
        return new PrePostTemplateDefaults();
    }

    /**
     * 密码加密器：BCrypt（自带盐、抗彩虹表），所有用户口令必须加密后入库。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 过滤链：无状态 + 白名单放行 + 其余 authenticated + JWT 过滤器。
     *
     * @param http           HttpSecurity 构建器
     * @param context        应用上下文（按名取 auth-platform-system 的 JWT 过滤器）
     * @param mapper         Jackson 序列化器（401/403 JSON 输出）
     * @param localeResolver i18n Locale 解析器（安全链早于 DispatcherServlet，需显式解析）
     * @return 安全过滤链
     * @throws Exception Spring Security 构建异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApplicationContext context,
                                                   ObjectMapper mapper, LocaleResolver localeResolver) throws Exception {
        // 白名单：登录 / 探活 / API 文档 / 公开字典 / 错误转发 / 健康检查 / 开放平台回调（钉钉企微，验签保护）
        String[] whitelist = {
                "/auth/login", "/ping", "/oauth/**", "/login", "/logout",
                "/.well-known/**", "/service/**",
                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                "/dict/*", "/error", "/actuator/health",
                "/notify/callback/**"
        };

        http
                // 前后端分离：禁用 CSRF（用 JWT 而非 Cookie-Session）
                .csrf(csrf -> csrf.disable())
                // 无状态会话
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 关闭默认表单/Basic 认证入口（统一走 401 JSON）
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // CORS 预检放行（开发期前端直连场景）
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(whitelist).permitAll()
                        // 其余接口一律需要登录
                        .anyRequest().authenticated())
                // 未认证：HTTP 401 + Result JSON（前端 axios 拦截器按 401 跳登录）
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, ex) ->
                                writeUnauthorized(response, mapper, localeResolver.resolveLocale(request)))
                        // 已登录但权限不足（@RequirePerm 拒绝）：403 + Result JSON
                        .accessDeniedHandler((request, response, ex) ->
                                writeForbidden(response, mapper, localeResolver.resolveLocale(request))));

        // 挂载 auth-platform-system 的 JWT 过滤器（模块未装配时保持白名单+全拦截的安全默认）
        if (context.containsBean(JWT_FILTER_BEAN_NAME)) {
            OncePerRequestFilter jwtAuthFilter = context.getBean(JWT_FILTER_BEAN_NAME, OncePerRequestFilter.class);
            http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        }
        // 挂载 auth-biz-oidc 的服务间令牌过滤器（/service/**：client_credentials service token 自管认证）
        if (context.containsBean(SERVICE_FILTER_BEAN_NAME)) {
            OncePerRequestFilter serviceTokenFilter = context.getBean(SERVICE_FILTER_BEAN_NAME, OncePerRequestFilter.class);
            http.addFilterBefore(serviceTokenFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    /**
     * 输出 401 统一响应体（文案按请求 Accept-Language 本地化）。
     *
     * @param response 响应对象
     * @param mapper   Jackson 序列化器
     * @param locale   请求解析出的 Locale（zh_CN/en）
     * @throws java.io.IOException 写出失败
     */
    private void writeUnauthorized(HttpServletResponse response, ObjectMapper mapper, Locale locale) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(mapper.writeValueAsString(Result.fail(
                ErrorCode.UNAUTHORIZED.getCode(),
                AuthMessages.getMessage(ErrorCode.UNAUTHORIZED.getCodeKey(), null, locale, ErrorCode.UNAUTHORIZED.getMessage()),
                ErrorCode.UNAUTHORIZED.getCodeKey())));
    }

    /**
     * 输出 403 统一响应体（已登录但权限码不足，文案本地化）。
     *
     * @param response 响应对象
     * @param mapper   Jackson 序列化器
     * @param locale   请求解析出的 Locale（zh_CN/en）
     * @throws java.io.IOException 写出失败
     */
    private void writeForbidden(HttpServletResponse response, ObjectMapper mapper, Locale locale) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(mapper.writeValueAsString(Result.fail(
                ErrorCode.FORBIDDEN.getCode(),
                AuthMessages.getMessage(ErrorCode.FORBIDDEN.getCodeKey(), null, locale, ErrorCode.FORBIDDEN.getMessage()),
                ErrorCode.FORBIDDEN.getCodeKey())));
    }
}
