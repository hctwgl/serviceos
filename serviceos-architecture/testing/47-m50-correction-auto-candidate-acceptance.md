---
title: M50 整改 Task 自动候选人验收
version: 0.1.0
status: Implemented
---

| ID | 优先级 | 场景 | 证据 |
|---|---|---|---|
| M50-ASN-001 | P0 | 源 Task 有 RESPONSIBLE 时整改 Task 获得同 principal CANDIDATE | `CorrectionCasePostgresIT` |
| M50-ASN-002 | P0 | 无 RESPONSIBLE 时整改 Task 无 CANDIDATE，Case 仍 IN_PROGRESS | `CorrectionCasePostgresIT` |
