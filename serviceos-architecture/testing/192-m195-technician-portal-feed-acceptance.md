---
title: M195 Technician Portal Feed 验收矩阵
status: Implemented
milestone: M195
lastUpdated: 2026-07-17
---

# M195 Technician Portal Feed 验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M195-01 | ACTIVE TechnicianProfile + NetworkTechnicianMembership + `task.readAssigned` 列出本人 ACTIVE 责任 Feed | 仅见本人 assignee 任务；不含他人/跨网点 | pass（TechnicianPortalFeedPostgresIT） |
| M195-02 | 非师傅或伪造 `X-Technician-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（PostgresIT + Security） |
| M195-03 | 师傅 A 请求网点 B（无师傅成员） | 403 `PORTAL_CONTEXT_INVALID`；不泄露 B 数据 | pass（TechnicianPortalFeedPostgresIT） |
| M195-04 | 责任 ENDED 后带 `sinceCursor` | tombstone 仅 taskId + invalidationReason | pass（TechnicianPortalFeedPostgresIT） |
| M195-05 | schedule fan-in ACTIVE 任务预约 | 非敏感字段；无地址泄漏 | pass（TechnicianPortalFeedPostgresIT） |
| M195-06 | sync-summary 轻量计数 | pending / appointments / tombstones | pass（TechnicianPortalFeedPostgresIT） |
| M195-07 | 未认证访问 technician/me | 401 | pass（Security） |
| M195-08 | Admin Web：选 TECHNICIAN context → Feed 页带 `X-Technician-Context`；伪造上下文失败关闭 | UI 与 API 一致 | pass（E2E spec，需 Keycloak + TECHNICIAN 人格时） |
| M195-09 | 契约与模块边界 | Core OpenAPI 0.87.0、Flyway 095/97、ArchitectureTest | pass（contracts + arch） |
| M195-10 | 不实现 mobile-work-packages status | 无该 HTTP 路径 | pass（契约审查） |

## 证据

- `TechnicianPortalFeedPostgresIT`
- `TechnicianPortalControllerSecurityTest`
- `ArchitectureTest`
- `serviceos-admin-web/tests/e2e/technician-portal-feed.spec.ts`
- Core OpenAPI `0.87.0`
