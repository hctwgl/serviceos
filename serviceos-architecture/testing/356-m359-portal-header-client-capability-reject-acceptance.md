---
title: M359 Feed/详情头客户端能力拒单验收矩阵
status: Implemented
milestone: M359
lastUpdated: 2026-07-19
---

# M359 Feed/详情头客户端能力拒单验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M359-HDR-001 | P0 | iOS 详情遇不兼容冻结 FORM | 422 `CLIENT_CAPABILITY_UNSUPPORTED` | ProbeTest + ControllerSecurity |
| M359-HDR-002 | P0 | UNKNOWN clientKind | 跳过预检，不读 Bundle | ProbeTest `unknownClientSkips…` |
| M359-HDR-003 | P0 | Feed ASSIGNMENT 不兼容 | 返回 `clientCapabilityUnsupportedDetail`，任务仍在列表 | QueryService + OpenAPI |
| M359-HDR-004 | P0 | Feed 不整页拒单 | 兼容与不兼容项可同页 | 设计 + QueryService |
| M359-HDR-005 | P0 | OpenAPI 1.0.53 | Feed 字段 + 详情 422；client-ts | contracts + client-ts |
| M359-HDR-006 | P1 | H5 展示并阻断深链 | 说明可见；不可打开任务详情 | technician-web Feed 页 |
| M359-HDR-007 | P0 | ArchitectureTest | readmodel 仅依赖 configuration::api | arch |
| M359-HDR-008 | P0 | 既有 Feed IT | 签名兼容、无回归 | TechnicianPortalFeedPostgresIT |

## 明确不在本矩阵

- iOS 条件执行器；派单过滤；UNKNOWN 强制升级；整改头级专用门禁；Flyway 变更。
