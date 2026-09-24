package com.auth.framework.config;

import com.auth.framework.log.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <p>
 * 开发期前端 Vite dev server (5173) 直连后端 (8080)，生产期同源部署，
 * 因此开放 CORS 仅作为开发便利项；生产环境建议由 Nginx 同源转发，可关闭。
 * <p>
 * 同时注册链路 ID 过滤器 {@link TraceIdFilter}：显式用 FilterRegistrationBean 指定
 * 最高优先级，保证早于 Spring Security 过滤链（默认 order -100）执行——
 * 若改为 @Component 自动注册会落到默认顺序（LOWEST_PRECEDENCE），401 日志将无 traceId。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                // 暴露链路 ID 头，前端出错提示时可展示便于按 ID 检索后端日志
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 链路 ID 过滤器注册（整条过滤链最先执行）。
     *
     * @return 过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.addUrlPatterns("/*");
        registration.setName("traceIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
