---
title: M100 运营异常项目范围硬化
status: Implemented
milestone: M100
---

# M100 运营异常项目范围硬化

## 1. 目标

窄接受并硬化 API-06 §6 `GET /operational-exceptions`：将 M29 租户级工作台升级为
实时 TENANT/PROJECT/REGION/NETWORK 项目范围约束，游标绑定 scopeDigest 与全部筛选。

## 2. 查询语义

- 新增 `projectId` 筛选；缺省时通过 `ProjectScopeAuthorizationService` 解析授权项目集合；
- 非 TENANT 范围主体看不到 `project_id` 为空的孤儿异常（失败关闭）；
- 排序保持 `openedAt DESC, exceptionId DESC`；
- 游标变化（范围或筛选）返回 `VALIDATION_FAILED`；
- Capability：`operations.exception.read`；确认动作在有 project 时改用项目能力校验。

## 3. 数据与契约

- Flyway **V084** 增加可空 `project_id`，从 WorkOrder/Task 回填，并建立项目队列索引；
- Core OpenAPI **0.70.0** 增加 `projectId` 查询参数与响应字段；
- 当前 Flyway V084 / 86 migrations。

## 4. 明确未实现

通用 work-queues/SavedView、人工标记已送达/放弃、通用 RESOLVED UI、FACTS_CALCULATIONS、Portal。
