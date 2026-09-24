# auth-center 统一认证中心开发交接文档（新会话必读）

本仓库是一套自研**统一认证中心（IdP/IAM）**，为集团多系统（ERP `../sf-erp`、MES `../sf-mes`、后续更多）提供统一身份认证与应用授权。设计对标 **微软 Entra ID 应用模型 + 国内 4A 平台（账号/认证/授权/审计）+ Spring Authorization Server 的 OIDC 协议语义**。由前一会话完成调研与架构定稿，现移交新会话开发。**动手前先读本文档，再通读参照系。**

## 参照系（结构与规范的唯一模板）

`../sf-erp` —— 同栈 ERP 系统，分层/命名/REST/注释/文档格式/Flyway 版本段/前端组件四件套全部照抄它的规范：
- `erp/README.md`、`erp/docs/开发规范.md`（最重要）、`docs/架构设计.md`
- `erp/erp-backend/erp-base/erp-framework` 的通用组件（Result/PageResult/BusinessException/BaseEntity/OrgContextHolder/Idempotent/PageQuery/SecurityConfig 等），搬运改名复用（包名 `com.auth`）
- `erp/erp-web` 的 request.ts、PageTable/PageHeader/StatusTag/HasPerm、路由片段模式、三门户 layout

## 技术栈（与 ERP 一致）

- 后端：Java 21 + Spring Boot 3.3 + MyBatis-Plus 3.5.7 + MySQL 8（auth_db, root/auth123456）+ Redis 7 + Flyway + springdoc + JJWT + Hutool + MapStruct，包名 `com.auth`
- 前端：React 18 + TypeScript strict + Vite + AntD 5 + Tailwind + TanStack Query + Zustand + react-router 6
- 前后端同仓：`auth-backend/` + `auth-web/`；docker-compose.yml（MySQL+Redis）；script/sql/migration（Flyway，仓库根为工作目录）
- **OIDC 实现选型**：优先评估引入 `spring-authorization-server`（官方授权服务器）；若与 MyBatis-Plus 多模块结构或既有 JWT 体系冲突，则基于 Spring Security + JJWT 自研协议端点，**协议语义严格对标 RFC 6749（OAuth2 授权码模式 + PKCE）与 OpenID Connect Core 1.0**：/authorize、/token、/userinfo、/jwks、/.well-known/openid-configuration、单点登出

## 已定稿的架构决策（不要推翻）

### 1. 职责边界："物业门禁"模型
认证中心管：**人（主账号生命周期）、门（应用分配）、店长（系统管理员标签）**。各业务系统管：自己店内的角色→菜单/按钮/数据权限。认证中心永远不存任何业务系统的菜单。

### 2. 主账号生命周期（全部只在认证中心）
- 入职建号（姓名/工号/组织/岗位）、转岗（改组织，事件留痕）、离职（全局停用，一键全系统失效）、全局状态：在职/离职/全局停用
- 匹配/关联属性用不可变的工号（externalId 思路，SCIM 借鉴），不用邮箱
- 离职是最强状态：离职后一切应用级启用/禁用操作被中心接口拒绝

### 3. 应用注册与分配
- 应用注册表：client_id、密钥、回调地址、令牌有效期、应用声明的角色标签（当前仅需要 `xxx.admin`）
- 用户×应用分配关系：分配后用户才能登录该应用（对标 Entra "User assignment required"）
- 业务系统管理端的用户列表 = 已分配到本应用的用户（同步副本），不是全公司名单

### 4. 应用级禁用（本方案关键设计）
- ERP/MES 管理员可在自己系统内对某员工"禁用/启用"，实际调用认证中心的 REST API 修改"该用户×该应用"的分配状态（仅影响本应用，不动全局状态）
- 前置校验：仅对在职员工可操作；离职/全局停用用户返回业务异常
- 认证中心提供**服务间 API**（client_credentials 模式发放 service token，或专用 API Key + 签名），供业务系统调用：应用分配启停、用户增量/全量同步（预留 SCIM 2.0 风格：软删除 active=false、对账式同步）

### 5. 角色模型（简单版，后续可升级完整版）
- 当前版本：中心只分配"系统管理员"标签（如 `mes.admin`）+ 应用进门权；普通角色（报工员/计划员等）由各系统管理员在系统内部自授
- 预留升级路径：应用在注册时声明完整角色清单，中心分配普通角色，token roles claim 携带（设计文档中写清升级方式，本期不实现）

### 6. 认证与令牌
- OIDC 授权码模式 + PKCE；JWT access token 按应用签发，roles claim 只含该应用的角色（本期即 admin 标签）；组织/工厂/部门等属性作独立 claims（ABAC 预留）
- 统一登录页 + 授权确认页（consent 可配置跳过）；单点登出（front/back-channel）
- 业务系统侧对接（ERP/MES 作为 client）：MES 侧已在其 AGENTS.md 中预留 /sso/callback 与本地直登降级，本期 auth-center 需提供完整接入文档（client 注册、端点、claims 说明、验签 JWKS）

### 7. 管理权限（认证中心自身的 RBAC）
平台级角色：超级管理员（种子账号 bootstrap，仅首次部署引导用）、应用管理员（只能查看/操作自己应用的分配与用户列表）、审计员（只读日志）。参考 Keycloak FGAP 的资源级委托思想。

### 8. 审计（4A 双层审计的第一层）
登录日志（成功/失败、IP、UA）、账号生命周期事件（建号/转岗/离职/状态变更）、授权变更（分配/回收/应用级启停）、管理端操作日志。

## 目录结构

```
auth-center/
├── README.md / docker-compose.yml / AGENTS.md(本文档)
├── docs/（开发规范沿用 ERP + 《统一认证中心设计文档》：物业门禁模型、生命周期状态机、
│        OIDC 流程时序图、端点契约、业务系统接入指南、SCIM 预留设计）
├── script/sql/migration/   # Flyway：system V1x、idm(用户/组织) V10x、app(应用/分配) V20x、
│                          #  token/audit V30x……版本段错开，历史脚本不可改
├── auth-backend/           # Maven 多模块，照抄 ERP 分组：
│   ├── auth-base/          # auth-common + auth-framework（从 erp-framework 搬运改名）
│   ├── auth-platform/      # auth-platform-system(平台RBAC/审计) + auth-platform-idm(主账号/组织/生命周期)
│   ├── auth-biz/           # auth-biz-app(应用注册/角色声明/分配/应用级启停) + auth-biz-oidc(协议端点/令牌/JWKS)
│   └── auth-bootstrap/     # AuthApplication（@MapperScan("com.auth.**.mapper")）
└── auth-web/               # 管理端（用户/组织/应用/分配/审计）+ 统一登录页/授权页（独立路由，无需登录态）
```

## 分期计划

- **P1 骨架与 IDM**：工程搭建、framework 搬运、system/idm（主账号/组织/生命周期状态机）、Flyway、docker-compose、前端骨架、种子超管
- **P2 OIDC 核心**：应用注册、授权码+PKCE、令牌签发/JWKS/userinfo/discovery、统一登录页、单点登出、服务间 API（应用级启停/用户同步）、模拟一个 demo client 走通全流程
- **P3 管理端与审计**：应用管理界面（分配/回收/启停）、应用管理员委托视图、审计日志查询、README/docs 完整化、演示数据（两家业务应用+若干用户覆盖在职/离职/禁用场景）

## 质量要求

- 严格遵循 ../sf-erp/docs/开发规范.md；REST/命名/注释/Git 全套照抄
- 每完成一个模块：调用 code-review 技能评审并修复 P0/P1，对冗余实现调用 simplify 技能
- 验收：后端 `mvn package` 通过、前端 build 通过、docker-compose up 后 Flyway 全过、种子超管可登录、OIDC 授权码全流程可用 curl/脚本演示
- 环境：brew 在 /opt/homebrew/bin；GitHub 直连不稳定，Maven/npm 用阿里云镜像；依赖缺失先安装
