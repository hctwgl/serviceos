---
title: M218 Technician Portal Feed/日程 Accepted 字段展示
status: Implemented
milestone: M218
lastUpdated: 2026-07-17
relatedMilestones: [M195]
---

# M218 Technician Portal Feed/日程 Accepted 字段展示

## 目标

在 M195 Technician Portal shell 上展示既有 Accepted 非 PII 字段，并补齐门户内深链与
schedule `taskId` 水合。

## 范围与非目标

- 范围：ADR-056；Feed/Schedule/SyncSummary UI enrichment；可选 sinceCursor 增量；
  OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP/字段、离线工作包、TASK.DETAIL、MESSAGE、PII、GPS/上传。

## 已实现

- [x] ADR-056
- [x] Feed/Schedule/SyncSummary enrichment
- [x] E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
