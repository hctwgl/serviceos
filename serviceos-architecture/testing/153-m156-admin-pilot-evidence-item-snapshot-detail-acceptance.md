---
title: M156 Admin 资料项/资料快照详情页验收
status: Implemented
milestone: M156
lastUpdated: 2026-07-17
---

# M156 Admin 资料项/资料快照详情页验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M156-01 | Task 面板 → 资料快照详情 | 创建 Snapshot 后可见「打开资料快照」；新页签 `GET /api/v1/evidence-set-snapshots/{id}` 200 | `admin-pilot-smoke.spec.ts`（完结） |
| M156-02 | Workspace → 资料项详情 | Task 完结后可见「打开资料项详情」；点击 `GET /api/v1/evidence-items/{id}` 200 | `admin-pilot-smoke.spec.ts`（完结） |
| M156-03 | 试点验收登记 | `ADMIN-PILOT-08ED` | `verify-admin-smoke.sh` |

## 明确不做

- Visit 独立详情页；
- 详情页写命令；
- SavedView、inbound 列表、企业 OIDC。
