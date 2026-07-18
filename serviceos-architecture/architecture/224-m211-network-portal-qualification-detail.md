---
title: M211 Network Portal 资质详情只读 UI
status: Implemented
milestone: M211
lastUpdated: 2026-07-17
relatedMilestones: [M205, M210]
---

# M211 Network Portal 资质详情只读 UI

## 目标

在 M205 GET technician-qualifications/{id} 之上交付只读详情页（含裁决字段与 version）。

## 范围与非目标

- 范围：ADR-049；`/network-portal/qualifications/:id`；列表深链；catalog 仍 v15；
  OpenAPI 仍 0.99.0；Flyway 仍 100/102；E2E。
- 明确不做：Portal decide、FileObject、新 HTTP/capability/pageId。

## 已实现

- [x] ADR-049
- [x] NetworkPortalQualificationDetailPage + 路由
- [x] 列表深链；无 decide 控件
- [x] E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
