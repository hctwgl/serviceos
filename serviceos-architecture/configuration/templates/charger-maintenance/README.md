---
title: 标准充电桩维修 Configuration 模板
status: Accepted
lastUpdated: 2026-07-18
---

# 标准充电桩维修 Configuration 模板

平台中立维修模板：受理 → 上门维修（可补偿）→ 等待车企确认。不含车企协议 DTO。

## 绑定约定

- `serviceProductCode`：`CHARGER_MAINTENANCE`
- WAIT_EVENT：`platform.oem.acknowledged` / `workOrder:{workOrderId}`
- 取消时 `ONSITE_REPAIR.compensation` 触发 `MAINT_UNDO_PARTS`
