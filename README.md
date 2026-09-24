# auth-center 统一认证中心（IdP/IAM）

集团统一身份认证与应用授权平台：管理全集团主账号生命周期、组织架构与应用访问分配，
为 ERP（../sf-erp）、MES（../sf-mes）等业务系统提供 OIDC 单点登录与服务间用户同步 API。

设计对标：微软 Entra ID 应用模型 + 国内 4A 平台 + OpenID Connect Core 1.0 / RFC 6749（授权码 + PKCE）。

## 快速开始

```bash
# 1. 基础设施（MySQL 13306 / Redis 16379，避开 ERP 的 3306/6379）
docker compose up -d

# 2. 后端（必须 JDK 21 + 仓库根为工作目录，Flyway 脚本在 script/sql/migration）
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -f auth-backend/pom.xml install -DskipTests
java -jar auth-backend/auth-bootstrap/target/auth-bootstrap-1.0.0-SNAPSHOT.jar

# 3. 前端（端口 5174，/api 代理到 8082）
cd auth-web && pnpm install && pnpm dev
```

- 后端：`http://localhost:8082/api`，Swagger：`/swagger-ui.html`
- 前端管理端：`http://localhost:5174`
- 种子超管：`admin / 123456`（**仅演示用，生产环境首登后必须改密**）
- OIDC 全流程演示：`./script/demo/demo-client-flow.sh`（demo-client / demo-secret）

## 文档

| 文档 | 内容 |
|------|------|
| [docs/系统概览.md](docs/系统概览.md) | **一页看懂**：服务/端点/全部表结构/Redis 键/启动方式 |
| [AGENTS.md](AGENTS.md) | 架构决策、目录结构、分期计划（开发必读） |
| [docs/统一认证中心设计文档.md](docs/统一认证中心设计文档.md) | 物业门禁模型、状态机、OIDC 时序、令牌体系、审计 |
| [docs/业务系统接入指南.md](docs/业务系统接入指南.md) | 业务系统接入：授权码+PKCE、验签 JWKS、服务间 API |
| [docs/开发进度.md](docs/开发进度.md) | 当前进度、环境事实、启动命令与坑 |

## 功能总览

- **主账号生命周期**：入职建号 / 改档 / 转岗留痕 / 离职（终态，一键全系统失效）/ 全局停用⇄在职 / 重置口令
- **组织架构**：树形组织 CRUD（成环/占用校验）
- **应用管理**：OAuth2 client 注册（密钥仅明文一次）、角色标签声明、启停、密钥重置
- **应用分配**：用户×应用进门权、应用级启停、角色标签（xxx.admin）授予
- **OIDC**：授权码 + PKCE（S256/plain）、RS256 access/id token、JWKS、discovery、userinfo、单点登出、client_credentials 服务令牌
- **服务间 API**：应用级启停、用户增量/全量同步（SCIM 风格对账）
- **审计**：登录日志（管理端+OIDC，成功/失败/IP/UA）、操作日志（写操作切面落库、脱敏）、生命周期事件流水

## 技术栈

后端：Java 21 + Spring Boot 3.3 + MyBatis-Plus + MySQL 8 + Redis 7 + Flyway + JJWT + springdoc
前端：React 18 + TypeScript(strict) + Vite + AntD 5 + Tailwind + TanStack Query + Zustand + react-router 6

## 目录

```
├── script/sql/migration   # Flyway：system V1x / idm V10x / app V20x / token+audit V30x
├── script/demo/           # OIDC 全流程演示脚本
├── auth-backend/          # Maven 多模块（base/platform/biz/bootstrap，包名 com.auth）
└── auth-web/              # 管理端 + 统一登录页/授权页（独立路由）
```
