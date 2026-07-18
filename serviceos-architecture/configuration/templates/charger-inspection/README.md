---
title: 标准充电桩巡检 Configuration 模板
status: Accepted
lastUpdated: 2026-07-18
---

# 标准充电桩巡检 Configuration 模板

平台中立巡检模板：排程 → 现场巡检 → EXCLUSIVE_GATEWAY（转维修 / 无异常结案）。不含车企协议 DTO。

## 绑定约定

- `serviceProductCode`：`CHARGER_INSPECTION`
- 网关示例条件以 serviceProductCode 演示分支；项目可在派生 Bundle 中覆盖为事实字段表达式
