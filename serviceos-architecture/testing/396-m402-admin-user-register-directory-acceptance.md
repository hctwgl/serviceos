---
title: M402 Admin 用户登记与目录摘要验收矩阵
version: 0.1.0
status: Implemented
milestone: M402
lastUpdated: 2026-07-20
---

# M402 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | register + persona | 创建主体与 Persona；无 IdentityLink/密码 | `IdentityDirectoryPostgresIT` |
| A2 | 工号冲突 | IDENTITY_PROFILE_CONFLICT | 同上 |
| A3 | 幂等重放 | 同一 Idempotency-Key 返回同一主体 | 同上 |
| A4 | 目录摘要 | 组织/角色列可见 | Playwright |
| A5 | 新建流程 | DedicatedFlow 可打开提交入口 | Playwright |
| A6 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`。
