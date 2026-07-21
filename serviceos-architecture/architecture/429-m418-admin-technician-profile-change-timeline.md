---
title: M418 Admin 师傅档案生命周期并入主体变更时间线
version: 0.1.0
status: Implemented
milestone: M418
lastUpdated: 2026-07-21
---

# M418 Admin 师傅档案生命周期并入主体变更时间线

## 1. 目标

关闭 M417 剩余 UI_DATA_GAP「师傅档案创建/停用生命周期事件并入」：在用户详情变更时间线中
soft-gate 合并 `TechnicianProfile` 不可变目录事件。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.84** `TECHNICIAN_PROFILE` source；`omittedSources` 增加同枚举 |
| Network | `TechnicianProfileChangeTimelineContributor` ← `net_directory_event` |
| Identity | 既有 soft-gate 合并路径自动收录；与网点/师傅服务关系共用 `network.read` |
| Admin Web | 「师傅档案」来源标签与缺权 omitted 提示 |
| 证据 | `IdentityDirectoryPostgresIT` + ArchitectureTest + Playwright |

## 3. 权限

- 硬门禁：`identity.read`
- soft-gate：`network.read`（`TECHNICIAN_PROFILE`；缺权时与其他 network 来源一并 omitted）

## 4. 明确未实现

- `TECHNICIAN_CLIENT_KINDS_DECLARED` 并入主体时间线
- 独立安全活动流承载 `AUTHORIZATION_DENIED`
- 全国区县全量树 / 拼音索引 / 多级子品牌
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
