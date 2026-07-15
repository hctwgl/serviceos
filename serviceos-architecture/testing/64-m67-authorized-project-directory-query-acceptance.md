---
title: M67 项目授权目录与范围历史查询验收矩阵
status: Implemented
lastUpdated: 2026-07-15
---

# M67 项目授权目录与范围历史查询验收矩阵

| ID | 优先级 | 场景 | 预期 |
|---|---|---|---|
| M67-LST-001 | P0 | TENANT `project.read` 查询目录 | 单条 SQL 返回租户项目，按 code/id 稳定排序 |
| M67-LST-002 | P0 | PROJECT/REGION/NETWORK grants 混合 | 实时映射取并集，不返回越权项目且不逐行鉴权 |
| M67-FLT-001 | P0 | clientId/status/activeOn 筛选 | 精确匹配已有事实，不猜状态或默认日期 |
| M67-CUR-001 | P0 | 相同授权范围和筛选翻页 | 无重复/遗漏；nextCursor 只在存在后页时返回 |
| M67-CUR-002 | P0 | 授权关系或筛选变化后复用 cursor | 400 失败关闭，不回退首页 |
| M67-GET-001 | P0 | 读取获权项目详情 | 返回当前 Project、REGION/NETWORK 关系、ETag 和服务端 asOf |
| M67-HIS-001 | P0 | 分页读取范围修订历史 | 按 aggregateVersion 倒序返回 M66 冻结收据，历史不受后续修订改写 |
| M67-AUTH-001 | P0 | 无有效 RoleGrant、已撤权或越权项目 | 403/404；拒绝有审计，数据不泄露 |
| M67-TEN-001 | P0 | 跨 tenant 使用相同 projectId | 404，不暴露资源存在性 |
| M67-VAL-001 | P0 | 非法 status/clientId/limit/cursor | 400，不静默移除筛选或返回默认结果 |
| M67-MIG-001 | P0 | PostgreSQL 18 空库迁移 | V067 capability 和目录游标索引存在，69 个迁移成功 |
| M67-API-001 | P0 | Core OpenAPI 与客户端生成 | 0.38.0 契约可解析、兼容门禁和生成重现通过 |
| M67-MOD-001 | P0 | 模块边界 | Project 仅依赖 Authorization/Identity 公共 API，ArchitectureTest 通过 |

## 未纳入本里程碑

owners、品牌/服务产品/配置绑定、项目生命周期写命令、外部目录层级、计划修订审批、导出分析和 Portal
前端不在 M67 验收范围内。

## 自动化证据映射

| 验收范围 | 自动化证据 |
|---|---|
| LST/FLT/CUR/GET/HIS/AUTH/TEN/VAL/MIG | `ProjectQueryPostgresIT`（PostgreSQL 18 Testcontainers） |
| HTTP/JWT/ETag/correlation | `ProjectControllerSecurityTest` |
| API/客户端 | `ContractValidationTest`、契约兼容脚本、客户端生成重现脚本 |
| 模块边界 | `ArchitectureTest` |
