---
title: M291 配置依赖分析
status: Implemented
milestone: M291
lastUpdated: 2026-07-18
relatedMilestones: [M282, M283, M268]
---

# M291 配置依赖分析

## 目标

对 WORKFLOW 草稿（及可选 Bundle 上下文）做确定性依赖扫描：提取 `formRef`/`slaRef`/`evidenceRef`/`subProcessRef`/`integrationRef`/`assigneePolicyRef`，报告 SATISFIED/MISSING/UNKNOWN_TYPE，失败关闭不猜测。

## 范围

1. `GET /configuration/drafts/{id}:dependencies`；
2. `POST /configuration/dependency-reports:analyze`（原始定义 + 可选 bundleId）；
3. Admin 设计器展示依赖报告；自动化 IT。

## 明确未实现

模拟执行、历史回放、跨租户依赖图 UI、自动补齐草稿。
