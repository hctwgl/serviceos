---
title: M286 配置 Bundle 灰度与回滚验收矩阵
status: Implemented
milestone: M286
---

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M286-01 | 激活 CANARY | ACTIVE | PASS |
| M286-02 | preferCanary 解析 | 命中 CANARY Bundle | PASS |
| M286-03 | 默认解析 | 仍用 STABLE | PASS |
| M286-04 | Promote | STABLE=原 CANARY | PASS |
| M286-05 | Rollback | STABLE 回到上一 Bundle | PASS |
