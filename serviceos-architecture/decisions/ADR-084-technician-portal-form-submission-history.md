---
title: ADR-084：Technician Portal 表单提交安全摘要
status: Accepted
date: 2026-07-18
---

# ADR-084：Technician Portal 表单提交安全摘要

## 决策

1. M243 详情增加可选 `formSubmissions`；除 `task.readAssigned` 外独立要求 project scope `form.read`；
2. 缺 `form.read` 时字段为 null/不渲染，不使任务详情整体失败，也不伪装为空业务集合；
3. 复用 Forms 已实现的安全摘要，只映射 submission/formVersion ID、formKey、提交版本、校验状态、
   错误/警告计数与 submittedAt；
4. 禁止 values、校验消息、contentDigest、prefillVersion、submittedBy 与配置 schema；
5. Core OpenAPI 升至 `1.0.20`；Flyway 100/102、Page Registry v16 不变；不接受表单草稿/提交写命令。

## 后果

任务详情可以按独立表单权限展示提交完成度，同时保持“任务可见不等于表单内容可见”的授权边界。
