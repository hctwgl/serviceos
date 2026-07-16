---
title: M90 工单工作区审核与整改区块
status: Implemented
milestone: M90
---

# M90 工单工作区审核与整改区块

## 1. 目标

扩展 API-06 §5 `workspace/sections/{section}` Accepted 范围，增加
`REVIEWS_CORRECTIONS` 实时组合按需加载。

## 2. 接受范围

- API-06 §5 新增 `REVIEWS_CORRECTIONS`；
- 复用 `workOrder.read` 入口鉴权，并按 Task 经 `evidence.read` 与实时 Project Scope
  装载 ReviewCase / CorrectionCase；
- 两个子集分别缺权降级为 null，不把整个工作区打成 403；
- 摘要不包含审核 note、approvalRef、decidedBy、整改 waiveNote 等自由文本或操作者信息。

## 3. 组合事实

| 子集 | 来源 |
|---|---|
| reviews | 工单 Task 列表后 `evidence::api` `ReviewCaseService.listForTask` 扇出 |
| corrections | 工单 Task 列表后 `evidence::api` `CorrectionCaseService.listForTask` 扇出 |

顶层 `sectionAvailability.REVIEWS_CORRECTIONS`：两边缺权 → UNAVAILABLE；两边空 → EMPTY；
否则 AVAILABLE。limit 分别截断 reviews / corrections；本切片不提供跨 Task 深分页 cursor。

## 4. 契约

Core OpenAPI **0.60.0**。无新 Flyway。

## 5. 明确未实现

其余 section、审核/整改命令聚合、工作队列/SavedView、Portal、区块持久化投影。
