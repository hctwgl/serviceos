---
title: 标准充电桩移机 Configuration 模板
status: Accepted
lastUpdated: 2026-07-18
---

# 标准充电桩移机 Configuration 模板

平台中立移机模板：受理 → 原址拆机 → 新址安装 → 等待车企确认。不含车企协议 DTO。

## 绑定约定

- `serviceProductCode`：`CHARGER_RELOCATE`
- WAIT_EVENT：`platform.oem.acknowledged` / `workOrder:{workOrderId}`
