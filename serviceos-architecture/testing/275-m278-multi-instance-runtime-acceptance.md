---
title: M278 多实例任务运行时验收矩阵
status: Implemented
milestone: M278
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M278-01 | 进入多实例 | 创建 N 个 ACTIVE | MultiInstance IT |
| M278-02 | 部分完成 | 不推进后继 | MultiInstance IT |
| M278-03 | 全部完成 | 推进下一节点 | MultiInstance IT |
| M278-04 | 发布校验 | 非法 cardinality/出边失败关闭 | Validator |
