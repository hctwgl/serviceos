---
title: M306 DISPATCH 运行时验收矩阵
status: Implemented
milestone: M306
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M306-01 | 过滤+评分+并列 | 同分按 candidateId；禁用候选拒绝 | DefaultDispatchRuntimeTest |
| M306-02 | 无候选 Fallback | MANUAL_INTERVENTION | DefaultDispatchRuntimeTest |
| M306-03 | policyKey 缺失 | RESOURCE_NOT_FOUND | DefaultDispatchRuntimeTest |
| M306-04 | 冻结 Bundle | 真实资产排序正确 | DispatchRuntimePostgresIT |
