---
title: M296 配置历史回放
status: Implemented
milestone: M296
lastUpdated: 2026-07-18
relatedMilestones: [M292, M268, M16]
---

# M296 配置历史回放

## 目标

对**已发布且被工单锁定的 Bundle**做无副作用回放：加载冻结 WORKFLOW 定义，按给定表达式上下文干跑，证明不读取“当前最新草稿/激活”。

## 范围

1. `POST /configuration/replays:run`（bundleId + ExpressionContext）；
2. 返回 Bundle 身份、锁定 WORKFLOW 版本与干跑轨迹；
3. Admin 设计器 Bundle 回放入口；Postgres IT。

## 明确未实现

按时间轴重放历史事件、并行全路径展开、写运行时表、跨 Bundle diff UI。
