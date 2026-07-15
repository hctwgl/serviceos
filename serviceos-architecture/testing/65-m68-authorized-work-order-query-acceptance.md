---
title: M68 授权工单目录与详情查询验收矩阵
status: Accepted
milestone: M68
---

# M68 授权工单目录与详情查询验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M68-01 | TENANT/PROJECT/REGION/NETWORK 授权并集查询 | PostgreSQL IT 只返回授权 project 的工单 |
| M68-02 | 状态、客户、项目筛选和稳定分页 | PostgreSQL IT 证明顺序、筛选和无重复分页 |
| M68-03 | 授权或筛选变化后重放 cursor | 400，不能退化为首屏或放宽范围 |
| M68-04 | 同租户越权详情 | 403 且产生具体资源拒绝审计 |
| M68-05 | 跨租户详情 | 404，不泄露资源存在性 |
| M68-06 | HTTP 身份与 ETag | MVC 测试证明主体来自 JWT 映射且详情返回版本 ETag |
| M68-07 | 数据最小化 | 契约和响应均不包含客户 PII/VIN/地址 |
| M68-08 | 架构、迁移、契约与全量门禁 | ArchitectureTest、PostgreSQL Testcontainers、契约门禁及 L3 通过 |
