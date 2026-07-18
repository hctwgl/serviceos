---
title: M277 SUB_PROCESS 运行时验收矩阵
status: Implemented
milestone: M277
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M277-01 | 进入 SUB_PROCESS | 父 WAITING + 子 ACTIVE | SubProcess IT |
| M277-02 | 子流程完结 | 父推进后继；工单仍 ACTIVE | SubProcess IT |
| M277-03 | 根流程完结 | 工单 FULFILLED | SubProcess IT |
| M277-04 | Bundle 多 WORKFLOW | 根解析唯一 | requireBundleAsset |
