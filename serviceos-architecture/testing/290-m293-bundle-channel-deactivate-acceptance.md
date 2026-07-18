---
title: M293 Bundle 通道停用验收矩阵
status: Implemented
milestone: M293
---

| 编号 | 场景 | 预期 |
|---|---|---|
| M293-01 | 停用 ACTIVE CANARY | status=SUPERSEDED；流量不再命中 |
| M293-02 | 停用 ACTIVE STABLE | resolve 失败关闭 |
| M293-03 | 非 ACTIVE 停用 | VERSION_CONFLICT |
| M293-04 | 版本冲突 | VERSION_CONFLICT |
