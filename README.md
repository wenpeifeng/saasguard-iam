# SaaSGuard IAM

> 面向 SaaS 产品和企业应用的可扩展、多租户身份与访问管理平台。

SaaSGuard IAM 把“人员主档、组织、应用入口、应用角色和审计”统一收口，为 ERP、MES、WMS、门户等系统提供统一登录与实时身份同步。项目采用 Java 21 + Spring Boot 3.3 与 React 18，内置可重复执行的 Flyway 初始化数据，首次启动即可体验两个彼此隔离的租户。

## 为什么做 SaaSGuard IAM

业务系统不应该各自创建一套人员账号。SaaSGuard IAM 作为身份事实源：

- 总部或租户管理员维护人员入职、转岗、停用、离职与密码；
- 为员工分配可访问的应用，以及应用声明的角色标签；
- 子系统只管理自己的菜单、按钮和数据权限，并通过 OIDC/JIT/对账接口获得人员；
- 人员或授权变化在事务提交后推送子系统，失败可追踪、可重试；
- 所有业务数据按 `tenant_id` 隔离，平台超级管理员才可显式切换租户视角。

## 已实现能力

| 领域 | 能力 |
|---|---|
| SaaS 租户 | 租户目录、状态、有效期、套餐、用户配额、品牌主色、语言、时区、首位租户管理员初始化 |
| 身份生命周期 | 入职建号、档案维护、转岗留痕、全局停用/恢复、离职终态、统一密码重置、不可变种子账号 |
| 组织 | 集团/公司/部门/车间/班组等组织类型，树形管理，编码与层级校验 |
| 应用与授权 | OAuth2 Client 注册、密钥单次展示与重置、用户应用分配、应用级启停、角色标签 |
| OIDC/OAuth2 | Authorization Code + PKCE S256、RS256 Access/ID Token、JWKS、Discovery、UserInfo、Logout、Client Credentials |
| 身份同步 | 子系统用户全量/增量查询、应用级档案维护、事务后实时对账事件 |
| 安全审计 | 登录成功/失败、IP、UA、管理写操作、敏感字段脱敏、生命周期事件；异步日志严格携带租户上下文 |
| 管理控制台 | 平台租户管理、主账号、组织、应用、登录日志和操作日志；平台管理员与租户管理员分级授权 |

> 当前协议实现覆盖项目所需的 OIDC 核心子集，并非完整认证服务器认证声明。动态客户端注册、MFA、WebAuthn、SAML、SCIM 2.0 标准端点和细粒度管理员委托在路线图中。

## 架构边界

```text
                         ┌──────────────────────────┐
                         │      SaaSGuard IAM       │
                         │ tenant / identity / app  │
                         │ OIDC / sync / audit      │
                         └────────────┬─────────────┘
                                      │
                  OIDC + JIT/对账 + Webhook/Service API
                 ┌────────────────────┼────────────────────┐
                 ▼                    ▼                    ▼
               ERP                  MES                  WMS
       角色→菜单/按钮/数据权限  角色→菜单/按钮/数据权限  本地业务权限
```

IAM 管“谁能进入哪个系统”；业务系统管“进入后能看什么、做什么”。SaaSGuard IAM 不保存 ERP/MES 的业务菜单。

## 快速开始

要求：JDK 21、Node.js 20+、pnpm、Docker。

```bash
# 1. MySQL 8 + Redis 7（端口 13306 / 16379）
docker compose up -d

# 2. 后端；必须从仓库根启动，Flyway 会读取 script/sql/migration
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -f auth-backend/pom.xml -DskipTests package
java -jar auth-backend/auth-bootstrap/target/saasguard-iam-server-1.0.0-SNAPSHOT.jar

# 3. 管理端（http://localhost:5174）
cd auth-web
pnpm install --frozen-lockfile
pnpm dev
```

- API：`http://localhost:8082/api`
- OpenAPI UI：`http://localhost:8082/api/swagger-ui.html`
- OIDC Discovery：`http://localhost:8082/api/.well-known/openid-configuration`
- 管理控制台：`http://localhost:5174`

### 开箱即用租户

| 租户代码 | 账号 | 密码 | 权限 | 用途 |
|---|---|---|---|---|
| `demo` | `admin` | `123456` | 平台超级管理员 | 租户管理和跨租户运维 |
| `acme-demo` | `acme_admin` | `123456` | 租户管理员 | 验证租户内用户、组织、应用与审计隔离 |

`demo` 还包含 `E3001`～`E3006` 只读演示员工与 `E3101`～`E3105` 可维护入职员工，口令均为 `123456`。这些数据全部由 Flyway 脚本创建，其他人克隆仓库并首次启动时会得到相同基线。

> 演示口令只能用于本地体验。部署到任何共享或生产环境前，必须移除/禁用演示账号，替换 JWT 密钥、数据库口令和 OIDC 签名密钥。

## 多租户 OIDC 接入要点

- 管理端登录 JSON 携带 `tenantCode`；
- 授权入口查询参数携带 `tenant_code`，未传时为兼容演示环境使用 `demo`；
- `/oauth/token` 表单携带 `tenant_code`，应用凭证只在该租户内校验；
- 授权码记录租户并与换令牌请求交叉校验；
- Access/ID/Service Token 均携带 `tenantId`，服务端进入数据层前恢复租户上下文；
- PKCE 只接受 `S256`，不允许降级为 `plain`。

完整接入说明见 [业务系统接入指南](docs/业务系统接入指南.md)。

## 项目结构

```text
├── auth-backend/
│   ├── auth-base/            # 公共模型、框架、多租户与安全基础设施
│   ├── auth-platform/        # system（租户/审计/RBAC）+ idm（用户/组织）
│   ├── auth-biz/             # app（应用授权/同步）+ oidc（协议端点）
│   └── auth-bootstrap/       # 可执行服务
├── auth-web/                 # React 管理控制台与 OIDC 登录/授权页
├── script/sql/migration/     # 可版本化、可重复交付的 Flyway 初始化数据
├── script/demo/              # OIDC 演示脚本
└── docs/                     # 架构、接入与验收文档
```

## 文档

- [SaaS 多租户 IAM 架构](docs/SaaS多租户IAM架构.md)
- [系统概览](docs/系统概览.md)
- [业务系统接入指南](docs/业务系统接入指南.md)
- [身份管理链路验收](docs/身份管理链路验收.md)
- [前端 UI 基线](auth-web/UI_STANDARDS.md)
- [贡献指南](CONTRIBUTING.md)
- [安全策略](SECURITY.md)

## 路线图

- 基于 Spring Authorization Server 的完整 OAuth 2.1 / OIDC Provider；
- MFA/TOTP、WebAuthn/Passkey、短信与邮件挑战；
- SCIM 2.0、标准 Webhook 投递箱与失败重放；
- SAML 2.0、外部企业 IdP 联邦和社交身份；
- 会话中心、设备管理、风险策略与租户级安全基线；
- 套餐能力开关、配额计量、租户自助品牌域名；
- 管理员委托、资源范围授权与更细粒度 ABAC。

## 开源与安全

项目以 [MIT License](LICENSE) 开源。代码库仅保留通用实现、公开依赖和演示数据，不应提交真实客户资料、生产密钥、私有仓库地址或内部基础设施配置。发现漏洞请按 [SECURITY.md](SECURITY.md) 私下报告，不要直接公开利用细节。
