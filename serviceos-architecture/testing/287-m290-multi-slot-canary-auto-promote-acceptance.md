---
title: M290 多槽位 CANARY 与满量自动晋级验收矩阵
status: Implemented
milestone: M290
---

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M290-01 | 双槽位并存 | slot-a/slot-b ACTIVE | PASS |
| M290-02 | 超额拒绝 | VALIDATION_FAILED | PASS |
| M290-03 | 累计分流 | 可见 STABLE 与 CANARY | PASS |
| M290-04 | 满量自动晋级 | STABLE=slot-a Bundle | PASS |
