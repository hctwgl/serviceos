---
title: M87 工单工作区按需区块加载验收矩阵
status: Implemented
milestone: M87
---

# M87 工单工作区按需区块加载验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M87-01 | TASKS 有任务 | 返回 tasks.items，meta/sourceVersions；无 PII |
| M87-02 | TIMELINE_AUDIT | 返回 timeline.items 与 freshnessStatus |
| M87-03 | 未接受 section | 400 VALIDATION_FAILED |
| M87-04 | 缺权/跨租户 | 与 M68/M85 一致（403/404） |
| M87-05 | 工程门禁 | OpenAPI 0.57.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |

不验收其他 section、队列、SavedView、Portal。
