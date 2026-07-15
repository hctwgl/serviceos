---
title: M69 授权工单执行工作区投影验收矩阵
status: Accepted
milestone: M69
---

# M69 授权工单执行工作区投影验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M69-01 | 获权工单已启动并推进 | PostgreSQL IT 返回冻结 Workflow、顺序 Stage 和对应 Task 摘要 |
| M69-02 | 已收单但 Workflow 尚未初始化 | Stage 响应明确返回 null/空数组，不伪造状态 |
| M69-03 | Task 稳定分页 | createdAt/taskId 正序，无重复遗漏，cursor 绑定 workOrderId |
| M69-04 | 同租户越权 | 403 且由 M68 鉴权链路写拒绝审计 |
| M69-05 | 跨租户 | 404，不泄露工单及执行资源存在性 |
| M69-06 | 数据最小化 | HTTP/OpenAPI 不包含 payload、resultRef、inputVersionRefs 或客户 PII |
| M69-07 | HTTP 身份和缓存版本 | MVC 证明主体来自可信映射；Stage/Task 返回 correlation ID |
| M69-08 | 工程门禁 | PostgreSQL 18、V069/索引、OpenAPI/客户端、ArchitectureTest 与 L3 通过 |
