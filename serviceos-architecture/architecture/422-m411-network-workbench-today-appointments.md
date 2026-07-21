---
title: M411 Network 工作台今日预约时间轴
version: 0.1.0
status: Implemented
milestone: M411
lastUpdated: 2026-07-21
---

# M411 Network 工作台今日预约时间轴

## 1. 目标

关闭 Network 工作台 CONTENT_GAP「今日预约时间轴」：首屏必须展示服务端运营日节奏桶与今日预约列表，不得由前端聚合猜测。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.77** `todayAppointmentCount` / `todayAppointments` / `todayTimeline` |
| ReadModel | Asia/Shanghai 运营日过滤 PROPOSED/CONFIRMED 预约；节奏桶含待分配/上午下午晚间预约/整改/SLA |
| Network Web | SummaryStrip「今日预约」+ 时间轴面板 + 今日预约表（无客户 PII） |

## 3. 权限

- 基座：`networkTask.read`（`todayTimeline` 至少含待分配）
- 今日预约列表/计数：`networkPortal.manageAppointment` soft-gate
- 师傅显示名：`technician.readOwnNetwork` soft-gate
- 整改/SLA 桶：沿用既有 soft-gate

## 4. 明确未实现

- 客户脱敏姓名（PII 读模型）
- 完整预约日历视图
- 推荐评分解释
- 产品负责人视觉金标
