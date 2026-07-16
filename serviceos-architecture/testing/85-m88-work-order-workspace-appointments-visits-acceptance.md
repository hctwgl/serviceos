---
title: M88 工单工作区预约与到访区块验收矩阵
status: Implemented
milestone: M88
---

# M88 工单工作区预约与到访区块验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M88-01 | 有 visit.read/appointment.read 且有 Visit | section 返回 visits；无 GPS/note/address |
| M88-02 | 仅 workOrder.read | 顶层 APPOINTMENTS_VISITS=UNAVAILABLE；section 可降级 |
| M88-03 | 两边空且有读权 | visits/appointments 空列表；顶层 EMPTY |
| M88-04 | 未接受 section 仍 400 | 与 M87 一致 |
| M88-05 | 工程门禁 | OpenAPI 0.58.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |

不验收其余 section、队列、SavedView、Portal。
