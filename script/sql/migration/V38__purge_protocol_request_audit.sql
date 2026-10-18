-- 登录、登出与 OAuth/OIDC 协议请求由专门日志覆盖，不应进入管理操作日志。
-- 早期过滤器未剥离 /api context-path，可能误记录表单中的口令或客户端密钥；统一清理。
DELETE FROM audit_op_log
WHERE uri IN ('/api/auth/login', '/api/auth/logout', '/api/oauth/token', '/api/oauth/authorize',
              '/auth/login', '/auth/logout', '/oauth/token', '/oauth/authorize');
