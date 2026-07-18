---
title: M285 配置草稿 Diff 与发布审批门禁验收矩阵
status: Implemented
milestone: M285
---

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M285-01 | Diff | 非 identical 且含变更行 | PASS |
| M285-02 | 未审批发布 | VERSION_CONFLICT | PASS |
| M285-03 | 审批后发布 | PUBLISHED | PASS |
| M285-04 | 编辑 APPROVED | 回退 DRAFT | PASS |
| M285-05 | OpenAPI/Admin 构建 | 1.0.30 + vite build | PASS |
