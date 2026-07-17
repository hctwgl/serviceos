---
title: M168 Admin Canonical Message 独立详情页验收
status: Implemented
milestone: M168
lastUpdated: 2026-07-17
---

# M168 Admin Canonical Message 独立详情页验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M168-01 | Envelope → Canonical 独立页 | `打开 Canonical` → GET 200；`BYD:INSTALL:` | `admin-pilot-smoke.spec.ts` |
| M168-02 | 工作区 INTEGRATION → Canonical | `.canonical-links` → GET 200 | `admin-pilot-smoke.spec.ts` |
| M168-03 | 试点验收登记 | `ADMIN-PILOT-08CM` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、原文下载、ServiceNetwork。
