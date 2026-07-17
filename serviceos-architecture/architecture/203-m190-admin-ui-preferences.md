---
title: M190 Admin UI Preferences
status: Implemented
milestone: M190
lastUpdated: 2026-07-17
relatedMilestones: [M189]
---

# M190 Admin UI Preferences

## 目标

在 M189 Admin 个人 SavedView 之上，为 Admin Portal 提供个人 UI Preference：主题、密度、语言、
减少动画、默认 SavedView 绑定与可选列宽，经 HTTP 持久化并在 Admin Web 应用，且不改变授权真相。

## 范围与非目标

- 范围：
  - 窄接受 API-06 §9 Admin UI Preference 与 DATA-06 §7 个人 Admin 键白名单；
  - ADR-028：归属 `readmodel`；已认证主体 CRUD 自己的偏好；
  - Core OpenAPI `0.82.0` `/me/ui-preferences*`；
  - Flyway `V092` `rdm_ui_preference`；
  - 允许键：`theme`、`density`、`locale`、`reduceMotion`、`defaultSavedViews`、`columnWidths`；
  - Admin Web 偏好面板（至少 theme/density/reduceMotion）与 CSS 类应用；
  - 可选：Task/WorkOrder 页按 `defaultSavedViews` 绑定默认 SavedView；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E。
- 明确不做：
  - 共享偏好、Network/Technician Portal；
  - 共享 SavedView、搜索、work-queues、analytics；
  - Consumer Identity、评分、BUSINESS SLA、设计系统大改。

## 事实源

- `api/06-application-query-preference-http-api.md` §0 / §9.1（Admin 切片 Accepted）
- `data/06-application-projection-preference-logical-model.md` §0 / §7.1（Admin 切片 Accepted）
- ADR-028

## 设计要点

- tenant + principal 作用域；portal 固定 `ADMIN`；
- 键白名单失败关闭；禁止安全绕过类键；
- 按 key 乐观版本；PUT 可带 `expectedVersion`；
- 列表/应用偏好不授予 capability；业务 API 仍重新鉴权。

## 已实现

- [x] ADR-028
- [x] OpenAPI Core `0.82.0`
- [x] Flyway `V092`
- [x] `UiPreferenceQueryService` / `UiPreferenceCommandService` / `UiPreferenceController`
- [x] Admin Web 偏好面板 + theme/density/reduceMotion CSS
- [x] PostgresIT / Security MVC / ArchitectureTest / E2E

## 明确未实现

- 共享偏好 / Network/Technician；
- 完整列宽编辑 UI（契约与持久化已支持 `columnWidths`）；
- 设计系统级组件与多主题引擎。

## 工程证据

- Flyway：`db/migration/readmodel/V092__create_admin_ui_preference.sql`
- OpenAPI：`serviceos-core-v1.yaml` 0.82.0
- IT：`UiPreferencePostgresIT`
- MVC：`UiPreferenceControllerSecurityTest`
- E2E：`admin-ui-preferences.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,UiPreferencePostgresIT,UiPreferenceControllerSecurityTest
bash scripts/verify-local.sh
```
