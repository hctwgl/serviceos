---
title: M134 Admin 试点可运行基线验收
status: Implemented
lastUpdated: 2026-07-16
---

# M134 Admin 试点可运行基线验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| ADMIN-PILOT-01 | Java/契约/Flyway 干净全量验证 | `bash scripts/verify-local.sh clean verify` | PASS |
| ADMIN-PILOT-02 | CI 同等日志敏感输出扫描 | 完整 Maven 日志 + `verify-sensitive-output.sh` | PASS |
| ADMIN-PILOT-03 | 敏感输出门禁正负样本 | 安全类型名通过；手机号/VIN 泄露被拒绝 | PASS |
| ADMIN-PILOT-04 | Admin 不可变依赖与生产构建 | `npm ci` + `npm run build` | PASS |
| ADMIN-PILOT-05 | 本地 OIDC 失败关闭 | 仅 DEV + 显式 env 开启；生产无 JWT 粘贴入口 | PASS |
| ADMIN-PILOT-06 | 真实登录 | Keycloak Authorization Code + PKCE + audience JWT | PASS |
| ADMIN-PILOT-07 | 真实授权 | Backend + PostgreSQL RoleGrant/Project Scope | PASS |
| ADMIN-PILOT-08 | 真实只读业务路径 | 目录 → 工作区 → 详情/Stage/Task/SLA/时间线 | PASS |
| ADMIN-PILOT-08A | 真实候选分配写路径 | 无 ACTIVE 候选 → Admin MANUAL assign-candidates → 最新批次/ACTIVE 候选/审计成立 | PASS |
| ADMIN-PILOT-08W | 真实 Task 写路径 | allowed-actions → claim → RESPONSIBLE → release → READY；真实 JWT、RoleGrant、候选责任、If-Match 与幂等键 | PASS |
| ADMIN-PILOT-08T | 真实 Task 终态推进 | 每轮新建 Workflow-backed HUMAN Task → assign/claim/start/complete → Inbox 消费 → Node/Stage/Workflow COMPLETED → WorkOrder FULFILLED | PASS |
| ADMIN-PILOT-08F | 真实表单完成引用 | RUNNING Task 锁定 FormVersion → `form.submit` → VALIDATED FormSubmission → 页面回填精确 ref/digest → complete；Evidence 未解析不阻断表单 | PASS |
| ADMIN-PILOT-08CI | 真实写链路 CI 阻断 | GitHub Actions `admin-pilot-e2e` 运行同一 OIDC/Backend/PostgreSQL/Chrome smoke；通过后才启动 staging | PASS |
| ADMIN-PILOT-09 | 完整履约写链路 | 接单→派单→预约→上门→表单/资料→审核/整改→外发→完结 | NOT PROVEN |

`ADMIN-PILOT-08A`、`ADMIN-PILOT-08W`、`ADMIN-PILOT-08T`、`ADMIN-PILOT-08F` 与
`ADMIN-PILOT-08CI` 只证明固定工单的候选分配/领取/释放，以及独立预置 Workflow Task 的表单提交、
完成和 END 推进；不证明资料、审核或从外部接单开始的完整履约链。
`ADMIN-PILOT-09` 是明确交付边界，不得用固定夹具的局部读写冒烟替代完整业务写链路验收。
