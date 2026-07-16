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
| ADMIN-PILOT-08F | 真实表单版本引用 | RUNNING Task 锁定 FormVersion → `form.submit` → VALIDATED FormSubmission → 页面保持精确 ref/digest 为双输入完成的主引用 | PASS |
| ADMIN-PILOT-08E | 真实资料双输入完成引用 | `task.created` Outbox/Inbox 解析 Slot → Begin → 同源私有 PUT → Finalize → 本地扫描 → 机器校验 VALIDATED → Snapshot → 页面自动提交 FormSubmission + EvidenceSetSnapshot 精确双引用 → complete | PASS |
| ADMIN-PILOT-08R | 真实资料审核通过 | Snapshot → createReviewCase → 独立审核页读取 OPEN → 普通 APPROVED decide → 权威详情/唯一决定历史 → 创建与裁决审计及两条事件 Inbox 成功 | PASS |
| ADMIN-PILOT-08C | 真实驳回与整改豁免 | 独立 Snapshot → 普通 REJECTED → 自动 IN_PROGRESS CorrectionCase/整改 Task → 授权队列/详情 → CRITICAL WAIVED → 整改 Task CANCELLED；三类审计与四条审核/整改事件 Inbox 成功 | PASS |
| ADMIN-PILOT-08X | 真实强制通过与重开 | 独立 OPEN Case → CRITICAL FORCE_APPROVED → 原 Case REOPENED + 同 Snapshot 后继 OPEN；页面导航/刷新保持后继身份，三类审计与三条事件 Inbox 成功，无 CorrectionCase | PASS |
| ADMIN-PILOT-08CI | 真实写链路 CI 阻断 | GitHub Actions `admin-pilot-e2e` 运行同一 OIDC/Backend/PostgreSQL/Chrome smoke；通过后才启动 staging | PASS |
| ADMIN-PILOT-09 | 完整履约写链路 | 接单→派单→预约→上门→表单/资料→审核/整改→外发→完结 | NOT PROVEN |

`ADMIN-PILOT-08A`、`ADMIN-PILOT-08W`、`ADMIN-PILOT-08T`、`ADMIN-PILOT-08F`、
`ADMIN-PILOT-08E`、`ADMIN-PILOT-08R`、`ADMIN-PILOT-08C`、`ADMIN-PILOT-08X` 与
`ADMIN-PILOT-08CI` 只证明固定工单的候选分配/领取/释放，以及独立预置 Workflow Task 的表单提交、
本地资料上传/校验/Snapshot、普通 INTERNAL APPROVED 审核、双输入完成和 END 推进，以及另一
动态 Task 的普通 REJECTED、自动整改与授权 WAIVED/Task CANCELLED，以及 FORCE_APPROVED/
重开后继血缘；不证明生产对象存储/专业扫描、正常整改补传/关闭/复审、外部提审回执，或从
外部接单开始的完整履约链。
`ADMIN-PILOT-09` 是明确交付边界，不得用固定夹具的局部读写冒烟替代完整业务写链路验收。
