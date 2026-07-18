---
title: 标准家充勘测安装 Configuration 模板
status: Accepted
lastUpdated: 2026-07-18
---

# 标准家充勘测安装 Configuration 模板

平台中立模板包：不包含任何车企协议 DTO。车企差异通过 Connector + INTEGRATION Mapping + 独立 Bundle 绑定注入。

## 资产

| 文件 | 类型 | 说明 |
|---|---|---|
| `workflow.json` | WORKFLOW | 含 EXCLUSIVE_GATEWAY 与 WAIT_EVENT |
| `sla.json` | SLA | ELAPSED Task 时钟 |

## 绑定约定

- `serviceProductCode`：`HOME_CHARGING_SURVEY_INSTALL`
- Bundle 发布时由项目解析 `brandCode` / 区域；模板本身不写死车企
- WAIT_EVENT：
  - `platform.survey.confirmed` → `workOrder:{workOrderId}`
  - `platform.oem.acknowledged` → `workOrder:{workOrderId}`
