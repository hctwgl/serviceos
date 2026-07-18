---
title: M293 Bundle 通道停用
status: Implemented
milestone: M293
lastUpdated: 2026-07-18
relatedMilestones: [M286, M288, M290]
---

# M293 Bundle 通道停用

## 目标

对 ACTIVE STABLE/CANARY 激活执行显式停用（`SUPERSEDED`），不修改已发布 Bundle；停用 STABLE 后解析失败关闭，不停用回滚到未知默认。

## 范围

1. `POST /configuration/bundle-activations/{id}:deactivate`（If-Match + approvalRef）；
2. CANARY 槽位可独立停用并从流量预算移除；
3. 自动化 IT 证明停用与 resolve fail-closed。

## 明确未实现

定时停用、区域级停用、停用审批工作流、Admin 发布控制台完整 UI。
