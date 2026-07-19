---
title: M312 Workflow 画布属性面板与撤销重做
status: Implemented
milestone: M312
lastUpdated: 2026-07-19
relatedMilestones: [M287, M289, M310]
---

# M312 Workflow 画布属性面板与撤销重做

## 目标

增强 Admin Workflow 设计器：节点类型面板、属性编辑、即时校验高亮、小地图、撤销/重做；与 `workflow-v1.schema` 字段对齐。

## 范围

- `workflowCanvasModel.ts`：节点类型目录、默认节点、即时校验、nodeId 生成
- `WorkflowCanvas.vue`：
  - 添加 START/END/USER_TASK/SERVICE_TASK/REVIEW_TASK/WAIT_EVENT/TIMER/网关/SUB_PROCESS/MANUAL_INTERVENTION
  - 选中节点属性：name/stageCode/taskType/formRef/slaRef；WAIT/TIMER/SUB_PROCESS 专用字段
  - 校验失败节点红色高亮 + 错误列表
  - 小地图；撤销/重做（拖拽仅在 pointerup 记一次历史）
- Node 冒烟 + `npm run build`；Playwright `M312 节点属性面板与撤销重做`

## 明确未实现

- FORM/EVIDENCE/SLA 独立可视化配置器；全资产属性面板；多实例 cardinality UI；真实吉利 Sandbox

## 验证

```bash
node serviceos-admin-web/src/expression/workflowCanvasModel.test.mjs
cd serviceos-admin-web && npm run build
```
