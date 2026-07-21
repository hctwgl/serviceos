---
title: M413 Network 预约日历视图
version: 0.1.0
status: Implemented
milestone: M413
lastUpdated: 2026-07-21
---

# M413 Network 预约日历视图

## 1. 目标

关闭 Network `CONTENT_GAP`「完整预约日历视图」：网点可按运营日查看今日与未来预约节奏，不再只能依赖工作台今日列表或任务页操作表单。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.79** `GET /network-portal/appointment-calendar` |
| ReadModel | Asia/Shanghai 闭区间；默认 14 天；跨度 ≤31；PROPOSED/CONFIRMED；无客户 PII |
| Page Registry | `NETWORK.APPOINTMENT` → `appointments` / 标题「预约日历」；catalog **v21** |
| Network Web | `/network-portal/appointments` 运营日条 + 日明细；工作台「今日预约」深链 |

## 3. 权限

- 硬门禁：`networkPortal.manageAppointment`
- 师傅显示名：`technician.readOwnNetwork` soft-gate

## 4. 明确未实现

- 客户脱敏姓名/地址
- 月视图拖拽改约
- 数值推荐评分 / 路网距离
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
