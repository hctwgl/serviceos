---
title: M63 授权项目集合与跨项目 SLA 队列验收矩阵
version: 0.1.0
status: Implemented
---

# M63 授权项目集合与跨项目 SLA 队列验收矩阵

| 场景 | 优先级 | 输入/动作 | 预期证据 |
|---|---|---|---|
| M63-AUTH-001 | P0 | 多个有效 PROJECT RoleGrant | 去重合并精确 UUID 集合，不扩大 tenant 范围 |
| M63-AUTH-002 | P0 | 有效 TENANT RoleGrant | 查询 tenant 内全部项目，忽略更窄 PROJECT 对结果的限制 |
| M63-AUTH-003 | P0 | 只有 REGION/NETWORK RoleGrant | 因无权威项目映射返回 403，并记录拒绝审计 |
| M63-AUTH-004 | P0 | grant 缺失、过期或撤销 | 实时失败关闭，不信任 JWT capability |
| M63-AUTH-005 | P0 | PROJECT scope_ref 非 UUID | 授权数据完整性失败，不静默忽略 |
| M63-QRY-001 | P0 | 省略 projectId 查询 SLA 队列 | 一次范围解析、一条 tenant + project 集合 SQL，无逐行鉴权 |
| M63-QRY-002 | P0 | 非 tenantWide 空项目集合 | SQL 显式 `AND FALSE`，不能退化为 tenant 全量 |
| M63-CUR-001 | P0 | PROJECT grant 集合在翻页间变化 | scope digest 改变，旧游标返回 400 |
| M63-HTTP-001 | P0 | OpenAPI 0.34.0 省略 projectId | HTTP 接受请求并由应用层解析授权集合 |
| M63-MIG-001 | P0 | PostgreSQL 18 空库迁移 | 65 个迁移到达 v063，tenant 游标索引存在 |
| M63-MOD-001 | P0 | SLA 跨模块调用授权集合 | 仅使用 `authorization::api`，ArchitectureTest 通过 |

本矩阵不验收 REGION/NETWORK 项目关系投影、授权缓存、BUSINESS 时钟、暂停、预警、通知、Portal 或结算。
