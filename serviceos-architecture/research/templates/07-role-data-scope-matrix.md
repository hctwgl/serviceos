---
title: M1-07 角色与数据范围矩阵模板
version: 0.1.0
status: Draft
---

# M1-07 角色与数据范围矩阵模板

## 角色能力

| 角色 | 创建 | 查看 | 编辑 | 派单/改派 | 审核 | 强制通过 | 关闭/恢复 | 试算/调整 | 导出 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 品牌负责人 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |
| 项目经理 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |
| 客服 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |
| 网点负责人 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |
| 师傅 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

## 数据范围

| 角色 | 客户/品牌 | 项目 | 区域 | 网点 | 工单关系 | 字段脱敏 | 金额可见 | 附件可见 |
|---|---|---|---|---|---|---|---:|---:|
| 项目经理 | TBD | TBD | 指定区域 | TBD | TBD | TBD | TBD | TBD |
| 网点负责人 | TBD | TBD | TBD | 所属网点 | 当前分配 | TBD | TBD | TBD |
| 师傅 | TBD | TBD | TBD | 所属网点 | 分配给自己 | TBD | TBD | TBD |

## 流程动作约束

| 动作 | 允许角色 | 数据范围条件 | 任务状态条件 | 是否二次确认 | 是否审批 | 必填原因 |
|---|---|---|---|---:|---:|---:|
| 人工改派 | 项目经理/助理/客服 | 当前项目和区域 | TBD | 是 | TBD | 是 |
| 强制关闭 | TBD | TBD | TBD | 是 | TBD | 是 |
| 强制审核通过 | 特殊授权角色 | TBD | 待审核 | 是 | TBD | 是 |

## 授权治理

| 项目 | 规则 |
|---|---|
| 角色申请和审批 | TBD |
| 临时授权期限 | TBD |
| 离职/转岗回收 | TBD |
| 高风险权限复核周期 | TBD |
| 数据导出审计 | TBD |
