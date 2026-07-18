---
title: M287 Workflow 可视化拖拽画布
status: Implemented
milestone: M287
lastUpdated: 2026-07-18
relatedMilestones: [M284, M285]
---

# M287 Workflow 可视化拖拽画布

## 已实现

1. Admin `WorkflowCanvas`：SVG 连线 + 指针拖拽节点；
2. 布局写入 `metadata.layout[nodeId]={x,y}`，与 JSON 双向同步；
3. 自动排布；设计器默认定义含初始布局；
4. Playwright 冒烟（节点渲染 + 拖拽更新 layout）；Admin 生产构建通过。

## 明确未实现

画布内新建/删除边、条件编辑器、百分比流量灰度。
