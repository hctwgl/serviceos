---
title: M420 Admin 师傅客户端种类声明并入主体变更时间线
version: 0.1.0
status: Implemented
milestone: M420
lastUpdated: 2026-07-21
---

# M420 Admin 师傅客户端种类声明并入主体变更时间线

## 1. 目标

关闭 M418 剩余 UI_DATA_GAP「TECHNICIAN_CLIENT_KINDS_DECLARED 并入主体时间线」：修复事件 CHECK，
使声明可落库，并投影到用户详情变更时间线。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Flyway | **V145** 扩展 `ck_net_directory_event_type` 收录 `TECHNICIAN_CLIENT_KINDS_DECLARED` |
| Network | declare 写入 reason 快照；Contributor 投影该事件 |
| OpenAPI | **1.0.86**（change-timeline 描述同步；source 仍为 `TECHNICIAN_PROFILE`） |
| Admin Web | 变更记录展示「师傅客户端种类已声明」摘要 |
| 证据 | `IdentityDirectoryPostgresIT` + ArchitectureTest + Playwright |

## 3. 权限

- 与 M418 相同：硬门禁 `identity.read`；soft-gate `network.read`

## 4. 明确未实现

- 失败登录 / 设备指纹
- 全国区县全量树 / 拼音索引 / 多级子品牌
- 工单侧跨域时间线深化
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
