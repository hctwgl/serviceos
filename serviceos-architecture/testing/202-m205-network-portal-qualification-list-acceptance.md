---
title: M205 Network Portal 本网点资质只读列表验收矩阵
status: Implemented
milestone: M205
lastUpdated: 2026-07-17
---

# M205 Network Portal 本网点资质只读列表验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M205-01 | ACTIVE 成员 + `technician.readOwnNetwork` + 本网点师傅资质 | list 返回 | pass（NetworkPortalQualificationListPostgresIT） |
| M205-02 | 他网点师傅资质 | list 不返回 | pass（NetworkPortalQualificationListPostgresIT） |
| M205-03 | get 本网点资质 | 200 | pass（NetworkPortalQualificationListPostgresIT） |
| M205-04 | get 跨网点资质 | ACCESS_DENIED | pass（NetworkPortalQualificationListPostgresIT） |
| M205-05 | status / technicianProfileId 过滤 | 仅匹配项 | pass（NetworkPortalQualificationListPostgresIT） |
| M205-06 | 伪造 `X-Network-Context` | 403 PORTAL_CONTEXT_INVALID | pass（IT / E2E） |
| M205-07 | 未认证 HTTP | 401 | pass（NetworkPortalControllerSecurityTest） |
| M205-08 | 缺 `technician.readOwnNetwork` | 403 ACCESS_DENIED | pass（NetworkPortalQualificationListPostgresIT） |
| M205-09 | Admin Web `/network-portal/qualifications` | UI 可见；伪造拒绝 | pass（network-portal-qualification-list.spec.ts） |
| M205-10 | OpenAPI 0.97.0；Flyway 仍 100/102 | 契约与迁移门禁 | pass（contracts / preflight） |
| M205-11 | ArchitectureTest | readmodel / network api 边界 | pass（ArchitectureTest） |
