---
title: M400 项目实体选择器验收矩阵
version: 0.1.0
status: Implemented
milestone: M400
lastUpdated: 2026-07-20
---

# M400 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | TENANT 宽授权 | reference-options 返回租户内全部 clientId/regionCode 聚合 | `ProjectQueryPostgresIT` |
| A2 | REGION 授权 | 仅可见含该区域项目的 client；区域选项含这些项目的全部生效 REGION | 同上 |
| A3 | 空授权范围 | clients/regions 为空列表，不抛业务成功假象 | JDBC 仓储空集合 |
| A4 | Admin 新建项目 | 展示车企/区域/网点选择器，不要求粘贴 UUID | `admin-project-workbench-product.spec.ts` |
| A5 | 模块边界 | ArchitectureTest 通过 | `ArchitectureTest` |
| A6 | 契约 | OpenAPI 1.0.66 含新 schema/path | `serviceos-core-v1.yaml` |

产品状态：`READY_FOR_REVIEW`（不得宣称 PRODUCT_ACCEPTED）。
