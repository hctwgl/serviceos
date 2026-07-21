---
title: M415 Admin 跨聚合主体变更时间线验收矩阵
version: 0.1.0
status: Implemented
milestone: M415
lastUpdated: 2026-07-21
---

# M415 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 生命周期/登录 | 仍含 REGISTERED/LOGIN；无冗余审计 | `IdentityDirectoryPostgresIT` |
| A2 | 操作者显示名 | LOGIN 行 actorDisplayName=档案名 | 同上 |
| A3 | 任职合并 | MEMBERSHIP_CREATED 摘要含组织/单元 | 同上 |
| A4 | RoleGrant 合并 | REQUESTED/APPROVED 可见 | 同上 |
| A5 | soft-omit | 缺 organization/authorization 读权时 omittedSources 列出且不造假 | 同上 |
| A6 | Admin UI | 变更记录展示任职/授权/显示名 | Playwright |
| A7 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`。
