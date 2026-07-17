---
title: M188 Portal 上下文与导航
status: Implemented
milestone: M188
lastUpdated: 2026-07-17
relatedMilestones: [M187]
---

# M188 Portal 上下文与导航

## 目标

在 M183～M187 主体、组织、网点与 RoleGrant 治理之上，提供服务端计算的 Portal 上下文与
导航：`GET /api/v1/me`、`/me/contexts`、`/me/capabilities`、`/me/navigation`。Admin、
Network、Technician 独立消费；前端切换 context 不能扩权；导航不是授权事实。

## 范围与非目标

- 范围：
  - 扩展 `authorization`（ADR-026，不新建 portal 模块）；
  - Core OpenAPI `0.80.0` `/me*`；
  - Flyway `V090` Page Registry 覆盖与 feature gate；
  - 代码注册 Page Registry；contextVersion 绑定 grant generation；
  - Admin Web 消费 `/me/navigation`；Network/Technician 最小 stub；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E。
- 明确不做：
  - Consumer Identity / CustomerProfile / C 端注册与用户中心；
  - 完整 Network Portal / Technician App UI；
  - MFA obligation 执行器、SavedView、正式企业 IdP。

## 事实源

- `roadmap/03-identity-organization-governance-delivery-plan.md` §10
- `roadmap/04-identity-organization-governance-agent-worklist.md` §8
- `testing/identity-organization-governance-program-acceptance.md` §8
- `product/01-cross-portal-information-architecture.md`、`product/07-page-action-permission-matrix.md`
- ADR-026

## 设计要点

- 上下文只由有效 Persona、Membership、RoleGrant 计算；CONSUMER Persona 可存在但不产生上下文。
- `contextId` 必须来自 `/me/contexts`；伪造 network/project 上下文失败关闭。
- `expectedContextVersion` 不匹配时返回 `VERSION_CONFLICT`（409）。
- Page Registry：代码定 pageId/route/capabilities；DB 仅启用/标题/排序/feature gate。
- 导航隐藏后，业务 API 仍重新鉴权。

## 已实现

- [x] ADR-026
- [x] OpenAPI Core `0.80.0`
- [x] Flyway `V090`
- [x] `PortalContextQueryService` / `PortalContextController`
- [x] Affiliation/Persona 只读端口
- [x] Admin Web `/me/navigation` 消费 + Portal stubs
- [x] PostgresIT / Security MVC / ArchitectureTest / E2E

## 明确未实现

- Consumer Identity Epic；
- 完整 Network/Technician 产品 UI；
- MFA/obligation 执行器；
- 正式企业 IdP / HR Connector。

## 工程证据

- Flyway：`db/migration/authorization/V090__create_portal_page_registry_overrides.sql`
- OpenAPI：`serviceos-core-v1.yaml` 0.80.0
- IT：`PortalContextPostgresIT`
- MVC：`PortalContextControllerSecurityTest`
- E2E：`admin-portal-context.spec.ts`
- Seed：`grant-local-project-admin.sql` INTERNAL_EMPLOYEE Persona

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,PortalContextPostgresIT,PortalContextControllerSecurityTest
bash scripts/verify-local.sh
```
