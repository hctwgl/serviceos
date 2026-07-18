---
title: M281 标准维修/移机/巡检配置模板
status: Implemented
milestone: M281
lastUpdated: 2026-07-18
relatedMilestones: [M271, M280]
---

# M281 标准维修/移机/巡检配置模板

## 已实现

在家充勘安模板之外，新增平台中立模板包：

| 模板目录 | serviceProductCode | 要点 |
|---|---|---|
| `charger-maintenance` | `CHARGER_MAINTENANCE` | 维修 + WAIT_EVENT + 取消补偿声明 |
| `charger-relocate` | `CHARGER_RELOCATE` | 拆机/新址安装 + WAIT_EVENT |
| `charger-inspection` | `CHARGER_INSPECTION` | 巡检 + EXCLUSIVE_GATEWAY |

架构源与 `serviceos-backend/.../configuration-templates/` 同步；漂移测试覆盖；`StandardChargerServiceTemplatesPostgresIT` 验证三者可发布并启动首任务。

## 明确未实现

表单/资料/派单完整资产包、可视化设计器、项目级派生覆盖 UI、真实 OEM 绑定。
