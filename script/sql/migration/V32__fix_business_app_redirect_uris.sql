-- OIDC 授权码由浏览器回跳到各业务系统前端的 /sso/callback 路由。
-- 历史种子数据误登记为后端 API 端口，会导致 redirect_uri 精确校验失败。
UPDATE app_client
SET redirect_uri = CASE client_id
    WHEN 'erp-web' THEN 'http://localhost:5172/sso/callback'
    WHEN 'mes-web' THEN 'http://localhost:5173/sso/callback'
    ELSE redirect_uri
END,
update_time = NOW()
WHERE client_id IN ('erp-web', 'mes-web');
