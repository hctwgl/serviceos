---
title: M187 Admin 统一用户中心
status: Implemented
milestone: M187
lastUpdated: 2026-07-17
relatedMilestones: [M186, M188]
---

# M187 Admin 统一用户中心

## 目标

在已实现的 M183～M186 目录与治理 HTTP API（Core OpenAPI `0.79.0`）之上，为 Admin Web
建立统一用户中心操作面：用户、企业组织、合作组织/网点、师傅、角色与授权/委托。
运营人员通过安全目录搜索选择人员，不把 principal UUID 作为主要交互；写动作展示影响与
obligations，提交携带 `Idempotency-Key` / `If-Match`，成功后重读权威 API。

## 范围与非目标

- 范围：
  - Admin 页面与稳定 `pageId`（route name）：用户目录/详情、组织目录/详情、网点目录/详情、
    师傅目录/详情、角色目录/详情、授权与委托；
  - 手写 API 客户端（`securityPrincipals` / `organizations` / `networks` / `technicians` /
    `authorizationGovernance`）；
  - 共享组件：`PrincipalPicker`、`ImpactPanel`、`VersionedCommandForm`、
    `ExternalAuthoritativeBadge`；
  - Capability 门禁导航（M188 `/me` 前以目录读接口探测；深链失败关闭不泄露 PII）；
  - 本地 deploy seed 补齐 `organization.*` / `network.*` / `authorization.*` 能力；
  - 第二 Keycloak 用户 `viewer` 用于无治理能力深链 E2E；
  - 真实 Keycloak PKCE Playwright 覆盖 M187-01～05（及可行的 M187-06）。
- 明确不做：
  - M188 `/me` contexts/capabilities/navigation；
  - 新 Flyway（无结构变化）；
  - OpenAPI 版本 bump 或新造 BFF/端点；
  - Network Portal / Technician App；
  - 正式企业 IdP / HR Connector。

## 事实源

- `roadmap/03-identity-organization-governance-delivery-plan.md` §9
- `roadmap/04-identity-organization-governance-agent-worklist.md` §7
- `testing/identity-organization-governance-program-acceptance.md` §7
- Core OpenAPI `0.79.0` identity/organization/network/authorization 路径

## 设计要点

- 不发明 BFF：浏览器直连既有 `/api/v1` 契约，Bearer 来自本地 Keycloak PKCE。
- 目录选择器调用 `GET /security-principals?query=`，结果展示 displayName/employeeNumber。
- `EXTERNAL_AUTHORITATIVE` 组织显示只读徽章与 sourceSystem/sourceKey/同步指示；LOCAL 才开放
  结构写命令。
- 停用/终止/撤权/网点停用前展示 `ImpactPanel`；成功后重读详情与待重分配/清退摘要。
- 409 If-Match 冲突提示刷新并允许用新版本重试，不覆盖新事实。
- 低权限深链统一文案「无权访问或不存在」，不回显后端 detail。

## 已实现

- [x] Admin 用户中心路由与 pageId
- [x] API 客户端与共享组件
- [x] Capability 探测导航 + 深链失败关闭
- [x] deploy seed 能力与 viewer 主体
- [x] Playwright `admin-user-center.spec.ts`（接入 admin smoke）
- [x] 实现文档与验收矩阵
- 无新 Flyway；OpenAPI 保持 `0.79.0`。

## 明确未实现

- Portal `/me` 导航与正式 Page Registry 数据库启用（M188）；
- MFA/obligation 执行器；
- Network/Technician Portal UI；
- 正式 HR/企业微信 Connector 与生产 IdP。

## 工程证据

- 前端：`serviceos-admin-web/src/pages/*User*|Organization*|Network*|Technician*|Role*|Grant*`
- 组件：`PrincipalPicker` / `ImpactPanel` / `VersionedCommandForm` / `ExternalAuthoritativeBadge`
- E2E：`serviceos-admin-web/tests/e2e/admin-user-center.spec.ts`（M187-01～06 绿）
- Seed：`serviceos-deploy/keycloak/grant-local-project-admin.sql`、`realm-serviceos.json`
- Smoke：`serviceos-deploy/admin-pilot/verify-admin-smoke.sh`（`ensure_m187_viewer` 按实际 subject 回填）
- 运行时修复：主体目录查询适配器（ADR-091 迁移后为 `JooqIdentityDirectoryQueryRepository`）
  必须统一 `Instant` 时间映射，
  否则目录列表在真实 PostgreSQL JDBC 下 500，Capability 导航会隐藏用户入口

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash serviceos-deploy/admin-pilot/verify-admin-smoke.sh
# 或最小：npm run test:e2e -- tests/e2e/admin-user-center.spec.ts
bash scripts/verify-local.sh
```
