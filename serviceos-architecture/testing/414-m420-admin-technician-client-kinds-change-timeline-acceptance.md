---
title: M420 Admin 师傅客户端种类声明并入主体变更时间线验收矩阵
version: 0.1.0
status: Implemented
milestone: M420
lastUpdated: 2026-07-21
---

# M420 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 声明落库 | `declareTechnicianSupportedClientKinds` 可写入 directory_event | `IdentityDirectoryPostgresIT` |
| A2 | 时间线投影 | `TECHNICIAN_CLIENT_KINDS_DECLARED` 可见且摘要含种类 | 同上 |
| A3 | soft-omit | 缺 `network.read` 时与其他 TECHNICIAN_PROFILE 一并 omitted | 同上 |
| A4 | 模块边界 | ArchitectureTest | ArchitectureTest |
| A5 | Admin UI | 变更记录展示种类声明摘要 | Playwright |

产品状态：`READY_FOR_REVIEW`。
