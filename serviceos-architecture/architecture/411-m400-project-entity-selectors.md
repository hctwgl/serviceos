---
title: M400 项目车企/区域/网点实体选择器
version: 0.1.0
status: Implemented
milestone: M400
lastUpdated: 2026-07-20
---

# M400 项目车企/区域/网点实体选择器

## 1. 目标

关闭 Admin 项目创建与范围编辑中「粘贴 UUID/编码」路径，改为服务端授权范围内的实体选择：

- 车企：`GET /projects/reference-options` 聚合已授权项目的 `clientId`
- 区域：同一接口聚合生效 `prj_project_region.region_code`
- 网点：复用既有 `/service-networks` 目录（按名称/编码搜索多选）

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | Core **1.0.66**；`ProjectReferenceOptions` / `ProjectClientOption` / `ProjectRegionOption` |
| Backend | `ProjectQueryService.referenceOptions`；SQL 内收敛 tenant + 授权 project 范围；soft ACCESS_DENIED 走既有 project.read |
| Admin Web | `ProjectClientPicker`、`ProjectRegionPicker`、`NetworkEntityPicker`；接入项目新建与项目详情「服务范围/合作网点」 |
| Tests | `ProjectQueryPostgresIT#referenceOptions…`；`ProjectReferenceOptionsTest`；Playwright 新建流程可见选择器 |

## 3. 明确未实现

- 独立车企主数据目录（名称、品牌树、生命周期）
- 完整行政区名称树 / 拼音搜索字典
- Workflow/Task 模板实体选择器深化（与本里程碑无关）

## 4. 权限与范围

- `project.read`：读取 reference-options
- 网点目录沿用 ServiceNetwork 既有能力
- 前端不自报 tenant；不猜测区域显示名
