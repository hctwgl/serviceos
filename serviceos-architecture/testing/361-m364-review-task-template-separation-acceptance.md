---
title: M364 独立审核 REVIEW_TASK 模板分离验收矩阵（设计门禁）
status: Proposed
milestone: M364
lastUpdated: 2026-07-20
---

# M364 独立审核 REVIEW_TASK 模板分离验收矩阵（设计门禁）

本矩阵在 ADR-087 **Accepted** 前只验收设计完备性；Accepted 后追加工程行。

## 设计门禁（当前）

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M364-DES-001 | P0 | A1～A5 决策包成文 | ADR-087 Proposed 含推荐与替代 | ADR-087 |
| M364-DES-002 | P0 | 与 Correction 对称性说明 | A1-R 镜像 correctionTaskId | ADR-087 §3 |
| M364-DES-003 | P0 | 整改复审不篡改旧决定 | A4-R 对齐 architecture/10 §12 | ADR-087 |
| M364-DES-004 | P0 | 未接受前不改 latestMilestone | implementation-status 仍为 M363 | 本 PR |

## 工程门禁（ADR Accepted 后填写）

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M364-ENG-001 | P0 | create OPEN 绑定 reviewTaskId | 同事务 HUMAN Task | IT（待写） |
| M364-ENG-002 | P0 | APPROVED 只 complete reviewTaskId | 不碰源提交 Task | IT（待写） |
| M364-ENG-003 | P0 | 试点模板含 REVIEW_TASK | 种子可发布 | 模板 + 配置 IT |
| M364-ENG-004 | P0 | 整改 CLOSED→新 Case+Task | 旧决定只读 | IT（可拆下一切片） |
