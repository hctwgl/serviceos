---
title: M389 工单详情统一履约工作区产品化
status: Implemented
milestone: M389
lastUpdated: 2026-07-20
relatedMilestones: [M374, M385, M386]
openapiVersion: "1.0.64"
flywayVersion: "138"
---

# M389 工单详情统一履约工作区产品化

## 已实现

1. `BusinessProgress` 展示冻结阶段进度（当前/已完成/未开始）；
2. `AllowedActionBar` 展示服务端 allowed-actions 主操作；
3. 当前任务卡片（任务/阶段/网点/师傅/SLA + 命令面板）；
4. 右侧决策上下文：风险、责任链、外部集成、最近时间线；
5. Tabs 对齐母版文案（基本信息、任务记录、预约与上门、表单资料、审核与整改、外部回传、操作日志）；
6. Playwright + 1440 截图；产品状态 `READY_FOR_REVIEW`。

## 明确未实现

- 客户/地址 PII 正式读模型（仍诚实显示未提供）；
- 资料缩略图与完整审核记录产品化；
- 人工视觉批准。
