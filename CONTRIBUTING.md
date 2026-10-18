# 贡献指南

感谢参与 SaaSGuard IAM。提交代码前请先确认变更没有引入真实人员资料、生产凭证、私有依赖或内部网络地址。

## 本地验证

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -f auth-backend/pom.xml test

cd auth-web
pnpm install --frozen-lockfile
pnpm build
```

涉及数据库结构或演示数据时，请新增 Flyway 迁移，禁止修改已发布迁移；涉及租户数据时，必须使用至少两个租户验证隔离。

## 提交要求

- 一个提交聚焦一个问题，说明行为变化与验证方式；
- API、数据库和授权模型变化同步更新中文文档；
- 新接口明确平台管理员、租户管理员、应用管理员或服务账号边界；
- 登录、授权、审计与异步任务不得依赖未传播的 ThreadLocal 租户上下文；
- 不在日志、异常或响应中输出口令、客户端密钥、令牌和个人敏感信息。

安全漏洞不要提交公开 Issue，请按 [SECURITY.md](SECURITY.md) 报告。
