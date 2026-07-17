---
title: M215 Network Portal 工作区预约/联系尝试 fan-in
status: Implemented
milestone: M215
lastUpdated: 2026-07-17
relatedMilestones: [M213, M214, M197, M199]
---

# M215 Network Portal 工作区预约/联系尝试 fan-in

## 目标

在限定工单工作区上，客户端 fan-in 既有任务预约与联系尝试读 API，补齐 §6.1「预约」发现面。

## 范围与非目标

- 范围：ADR-053；工作区 related 预约/联系摘要 + 任务深链；缺能力省略；
  OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP/字段、SLA/Visit/表单 DTO、PII、Portal ACK、写控件。

## 已实现

- [x] ADR-053
- [x] Workspace appointment/contact fan-in
- [x] E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
