---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**PAUSED_AWAITING_OWNER**
- 上一闭环：M365（Draft PR #192，本地门禁已过；叠在 #191 / M364）
- 工程基线：**M365**（OpenAPI 1.0.57 / Flyway 133）
- 原因：下一切片需负责人选型，不得发明推进

## 已闭环

- M356～M363：Technician 客户端能力门禁垂直切片
- M364：独立审核 handling Task（ADR-087 A1-R～A5-R）— #191
- M365：REVIEW_TASK WAITING 门闸 + review-decided 唤醒（A5-B）— #192

## 请选型（回复字母即可）

| 选项 | 内容 | 风险/前置 |
|---|---|---|
| **A** | 派单级 `supportedClientKinds` 过滤 | 需 Accepted 设计包（过滤时机、失败关闭、与 M358 定向发布关系） |
| **B** | Network Portal on-behalf 能力门禁 | 需确认 `NETWORK_WEB` vs 代师傅端 `clientKind` 语义 |
| **C** | iOS 条件执行器全量硬阻断 | 本 Linux 环境多为 `BLOCKED_EXTERNAL`（需 Xcode/真机路径） |
| **D** | 暂停，等吉利联调材料 / AMOUNT·加权口径 / BUSINESS SLA 批准 | 硬门禁，不可发明 |

未选型前不开新里程碑 Draft PR，也不改 `latestMilestone`。
