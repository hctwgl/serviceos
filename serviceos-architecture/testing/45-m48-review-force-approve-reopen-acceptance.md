---
title: M48 ReviewCase 强制通过与重开验收
version: 0.1.0
status: Implemented
---

# M48 ReviewCase 强制通过与重开验收

| ID | 优先级 | 场景 | 证据 |
|---|---|---|---|
| M48-FA-001 | P0 | OPEN 强制通过写入 FORCE_APPROVED 决定与状态 | `ReviewCasePostgresIT` |
| M48-FA-002 | P0 | 缺少 reasonCodes/approvalRef 失败 | `ReviewCasePostgresIT` |
| M48-FA-003 | P0 | 普通 evidence.review 不能强制通过 | `ReviewCasePostgresIT` |
| M48-FA-004 | P0 | 强制通过复用 review-decided 事件且 decision=FORCE_APPROVED | `ReviewCasePostgresIT` |
| M48-RO-001 | P0 | APPROVED/FORCE_APPROVED 重开旧案 REOPENED + 新 OPEN | `ReviewCasePostgresIT` |
| M48-RO-002 | P0 | 重开后可再次裁决；旧决定保留 | `ReviewCasePostgresIT` |
| M48-RO-003 | P0 | 无 review.reopen 能力拒绝 | `ReviewCasePostgresIT` |
| M48-SEC-001 | P0 | 匿名强制通过/重开 401 | `ReviewCaseControllerSecurityTest` |
