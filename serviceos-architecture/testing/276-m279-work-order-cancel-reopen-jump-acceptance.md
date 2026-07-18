---
title: M279 工单取消/重开与流程人工跳转验收矩阵
status: Draft
milestone: M279
---

| 编号 | 场景 | 预期 |
|---|---|---|
| M279-01 | 取消 ACTIVE 工单 | WO CANCELLED；根流程 CANCELLED；开放任务/等待关闭 |
| M279-02 | 非法状态取消 | 失败关闭 |
| M279-03 | 重开 CANCELLED | WO ACTIVE；新根流程启动首任务 |
| M279-04 | 人工跳转 | 当前节点取消；目标任务 ACTIVE |
| M279-05 | 跳转未知节点 | 失败关闭 |
