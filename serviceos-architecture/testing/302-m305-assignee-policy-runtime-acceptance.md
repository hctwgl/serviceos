---
title: M305 ASSIGNEE_POLICY 运行时验收矩阵
status: Implemented
milestone: M305
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M305-01 | 高优先级策略命中 | 取 maxCandidates USER | DefaultAssigneePolicyRuntimeTest |
| M305-02 | 无策略命中 | Fallback ROLE_POOL | DefaultAssigneePolicyRuntimeTest |
| M305-03 | policyKey 缺失 | RESOURCE_NOT_FOUND | DefaultAssigneePolicyRuntimeTest |
| M305-04 | 冻结 Bundle 加载 | 真实发布资产可解析 | AssigneePolicyRuntimePostgresIT |
