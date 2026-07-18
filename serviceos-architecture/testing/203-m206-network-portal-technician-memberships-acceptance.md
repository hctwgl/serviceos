---
title: M206 Network Portal 师傅关系只读列表验收矩阵
status: Implemented
milestone: M206
lastUpdated: 2026-07-17
---

# M206 Network Portal 师傅关系只读列表验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M206-01 | ACTIVE 成员 + `technician.readOwnNetwork` → list | 返回本网点关系且含 version | pass（NetworkPortalTechnicianMembershipPostgresIT） |
| M206-02 | 他网点关系 | list 不返回 | pass（NetworkPortalTechnicianMembershipPostgresIT） |
| M206-03 | get 本网点关系 | 200 含 version | pass（NetworkPortalTechnicianMembershipPostgresIT） |
| M206-04 | get 跨网点关系 | ACCESS_DENIED | pass（NetworkPortalTechnicianMembershipPostgresIT） |
| M206-05 | status / technicianProfileId 过滤 | 仅匹配项 | pass（NetworkPortalTechnicianMembershipPostgresIT） |
| M206-06 | 伪造 `X-Network-Context` | 403 PORTAL_CONTEXT_INVALID | pass（IT / E2E） |
| M206-07 | 未认证 HTTP | 401 | pass（NetworkPortalControllerSecurityTest） |
| M206-08 | 缺 `technician.readOwnNetwork` | 403 ACCESS_DENIED | pass（NetworkPortalTechnicianMembershipPostgresIT） |
| M206-09 | Admin Web 终止表单填充真实 version | 非硬编码 1 | pass（network-portal-technician-memberships.spec.ts） |
| M206-10 | OpenAPI 0.98.0；Flyway 仍 100/102 | 契约与迁移门禁 | pass（contracts / preflight） |
| M206-11 | ArchitectureTest | 模块边界 | pass（ArchitectureTest） |
