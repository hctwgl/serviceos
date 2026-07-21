---
title: M404 Admin 用户详情组织任职编辑
version: 0.1.0
status: Implemented
milestone: M404
lastUpdated: 2026-07-20
---

# M404 Admin 用户详情组织任职编辑

## 1. 目标

关闭用户详情「组织归属」UI_DATA_GAP：展示带组织/单元显示名的任职，并支持创建、调动、终止。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.70** `GET /org-memberships?principalId` + `OrgMembershipSummary` |
| Organization | `listMembershipSummariesForPrincipal` 聚合显示名 |
| Admin Web | 用户详情组织归属：列表 + 创建/调动/终止 |

## 3. 明确未实现

- 完整变更审计时间线产品化
- 树形可视化拖拽调岗
- 产品负责人视觉金标

## 4. 权限

- 读取摘要：`organization.read`
- 写操作沿用既有 `organization.manageMembership`
- 外部权威组织标记只读结构，前端拒绝调动
