---
title: M183 统一主体目录
status: Implemented
milestone: M183
lastUpdated: 2026-07-17
---

# M183 统一主体目录

## 1. 范围

M183 建立 ServiceOS 内部稳定 `SecurityPrincipal`，将外部 OIDC `issuer + subject` 限定在不可变
`IdentityLink` 中，并补齐 `PersonProfile`、多 `Persona`、启停生命周期和安全目录 API。

本里程碑只实现主体目录，不实现 M184 组织树、M185 网点/师傅档案、M186 授权治理 UI、M187
统一用户中心和 M188 Portal context/navigation。

## 2. 权威模型与不变量

- `idn_security_principal`：tenant 内稳定 UUID，类型仅 `USER/SERVICE`，状态仅 `ACTIVE/DISABLED`；
- `idn_identity_link`：`(tenant_id, issuer, subject)` 全局唯一且数据库禁止 UPDATE/DELETE；一个主体可追加多个外部身份；
- `idn_person_profile`：显示名和 tenant 内可选唯一员工号；Profile 更新与主体聚合版本同事务；
- `idn_principal_persona`：主体可拥有多个不同类型 Persona，M183 只建立有效期与唯一性，不外推组织/网点职责；
- `idn_principal_lifecycle_event`：注册、绑定、Profile、Persona、启停的只追加事实，数据库禁止 UPDATE/DELETE；
- 不保存密码、refresh token、手机号、证件号或 IdP Secret；普通目录不返回 issuer/subject。

## 3. 认证与 JIT

Resource Server 先验证 JWT 签名、issuer 和 audience，再由 `PrincipalAuthenticationService` 使用
`tenant + issuer + subject` 解析内部 Principal。首次 USER 登录只有命中显式
`SERVICEOS_IDENTITY_JIT_ALLOWED_CONTEXTS=tenant|client` 白名单才允许 JIT；默认关闭，未知 SERVICE
主体永不 JIT。JIT 使用 identity-key transaction advisory lock，Principal、Profile、IdentityLink、
生命周期事实和审计同事务提交，避免并发首次登录产生双主体。

本地 Keycloak 管理员由部署 SQL 预置 IdentityLink，不依赖宽松回退。停用后每次 JWT 请求都会实时读取
Principal 状态，因此已签发旧 JWT 立即失败关闭；当前实现没有身份状态缓存，无需失效消息。

## 4. 命令、授权与事务

目录读写分别要求 `identity.read`、`identity.readSensitive`、`identity.manageLinks`、
`identity.manageLifecycle`、`identity.manageProfile`。identity 声明 `IdentityAuthorizationPort`，由
authorization 模块实现并复用实时 RoleGrant、Tenant Scope 和拒绝审计，保持模块依赖单向。

绑定、启停、Profile 和 Persona 命令均要求 `Idempotency-Key + If-Match`。同一事务内完成实时授权、
幂等占位、锁定、版本迁移、生命周期事实、审计和幂等结果；冲突返回 409，不静默覆盖。IdentityLink
按身份 advisory lock 后主体行锁的固定顺序，其他命令按主体行锁串行。

## 5. HTTP 与机器契约

- `GET /api/v1/security-principals`
- `GET /api/v1/security-principals/{principalId}`
- `GET /api/v1/security-principals/{principalId}/identities`
- `POST /api/v1/security-principals/{principalId}/identity-links`
- `POST /api/v1/security-principals/{principalId}:disable|enable|update-profile`
- `POST /api/v1/security-principals/{principalId}/personas`

Core OpenAPI 升级到 0.76.0；Flyway `V086` 建表并登记五项能力。目录分页 cursor 绑定筛选摘要，
tenant 只来自 `CurrentPrincipal`，客户端 tenant header/body 不参与授权。

## 6. 兼容与清理

既有本地 Keycloak subject 通过部署 SQL 显式映射到同 UUID Principal，使现有 RoleGrant 不变。
JWT subject 直通 `CurrentPrincipal.principalId` 的旧路径已删除，没有 legacy/fallback/双轨用户表。

## 7. 明确未实现

- 身份解绑、IdentityLink 修改、密码管理和本地万能用户；
- Organization/OrgUnit、NetworkMembership、TechnicianProfile；
- RoleGrant 申请审批、Delegation、职责分离治理；
- Admin 用户中心页面、正式企业 IdP 运维和 Portal context/navigation；
- 身份状态缓存、跨服务身份事件和 Broker 发布；当前模块化单体认证路径实时查库。

## 8. 证据入口

- `db/migration/identity/V086__create_unified_principal_directory.sql`
- `identity/domain/SecurityPrincipal.java`
- `identity/application/DefaultPrincipalAuthenticationService.java`
- `identity/application/DefaultSecurityPrincipalCommandService.java`
- `identity/web/SecurityPrincipalController.java`
- `serviceos-core-v1.yaml` 0.76.0
- `IdentityDirectoryPostgresIT`
- `SecurityPrincipalControllerSecurityTest`
- `SecurityContextCurrentPrincipalProviderTest`
- `ArchitectureTest`
