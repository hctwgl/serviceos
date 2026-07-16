---
title: M92 工单工作区服务责任摘要验收矩阵
status: Implemented
milestone: M92
---

# M92 工单工作区服务责任摘要验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M92-01 | dispatch.read + 当前 Task ACTIVE 网点/师傅责任 | 返回独立生效时间与原因码，SERVICE_ASSIGNMENT=AVAILABLE |
| M92-02 | 仅 workOrder.read | 摘要 null，SERVICE_ASSIGNMENT=UNAVAILABLE，工作区仍 200 |
| M92-03 | dispatch.read 但无 ACTIVE 责任 | 摘要 null，SERVICE_ASSIGNMENT=EMPTY |
| M92-04 | 敏感字段 | 不返回 assignment/saga/guard/decision/authority 内部 ID 或操作者 |
| M92-05 | 工程门禁 | OpenAPI 0.62.0、V080/82 migrations、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
