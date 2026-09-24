package com.auth.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 审计异步配置：登录/操作日志走独立线程池落库，失败不影响主流程。
 * 默认 SimpleAsyncTaskExecutor 足够（审计写入低频、短平快）。
 */
@Configuration
@EnableAsync
public class AuditAsyncConfig {
}
