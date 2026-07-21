---
title: M445 Admin 工单目录即将超时筛选
version: 0.1.0
status: Implemented
milestone: M445
lastUpdated: 2026-07-21
relatedMilestones: [M442, M444]
openapiVersion: "1.0.107"
---

# M445 Admin 工单目录即将超时筛选

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「即将超时窗口」：目录查询支持固定 30 分钟即将超时筛选。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.107**：`slaRisk` enum 含 `NEAR` |
| Backend | sla SPI `NEAR`：RUNNING 且 `now < deadline_at ≤ now+30m`；`Clock` 传入 now；`sla.read` 范围同 M442 |
| Admin Web | SLA 风险 Select「即将超时」 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 窗口固定 **30 分钟**（对齐演示 018；非 SlaMilestone 策略阈值）
- NEAR 不含 BREACHED
- 不改旁载列语义（仍 open/breached 计数）
- 无 Flyway、无新 capability

## 4. 明确未实现

- 策略化预警阈值、多档窗口、列上 remainingSeconds
- Network Portal 即将超时
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
