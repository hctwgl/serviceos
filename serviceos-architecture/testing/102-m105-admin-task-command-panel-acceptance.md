---
title: M105 Admin 人工任务命令面板验收矩阵
status: Implemented
milestone: M105
---

# M105 Admin 人工任务命令面板验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M105-01 | 按钮来源 | 仅展示 allowed-actions 返回的动作 |
| M105-02 | claim/start | 发送 Idempotency-Key + If-Match，无本地状态机 |
| M105-03 | release | 要求合法 reasonCode |
| M105-04 | complete | 要求 resultRef/resultDigest；双引用可附加 JSON |
| M105-05 | 成功后 | 重新加载工作区与 allowed-actions |
| M105-06 | 构建 | `npm run build` 通过 |
