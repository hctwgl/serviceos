---
title: M51 CorrectionCase WAIVED 验收
version: 0.1.0
status: Implemented
---

| ID | 优先级 | 场景 | 证据 |
|---|---|---|---|
| M51-WV-001 | P0 | IN_PROGRESS 豁免进入 WAIVED 并取消整改 Task | `CorrectionCasePostgresIT` |
| M51-WV-002 | P0 | 缺少 reason/approvalRef 失败 | `CorrectionCasePostgresIT` |
| M51-WV-003 | P0 | 普通 evidence.review 不能豁免 | `CorrectionCasePostgresIT` |
| M51-WV-004 | P0 | WAIVED 后不可 resubmit/close | `CorrectionCasePostgresIT` |
| M51-SEC-001 | P0 | 匿名 waive 401 | `CorrectionCaseControllerSecurityTest` |
