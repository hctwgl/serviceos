---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**PAUSED_AWAITING_OWNER**（无未阻塞可实施切片）
- 最近完成 Draft PR 栈：M356～M363（#183～#190）
- 栈尖：https://github.com/hctwgl/serviceos/pull/190
  （`cursor/m363-correction-lifecycle-capability-hard-reject-6a78`）
- latestMilestone（栈尖）：**M363**
- Flyway：**131**；OpenAPI：**1.0.56**
- `baselineCommit`：合并后回填

## 已闭环

Technician 客户端能力门禁垂直切片 **M356～M363**：

```text
发布门禁 → 运行时拒单 → 定向发布 → Feed/详情头
→ 整改资料路径 → 列表预检 → 领取/启动硬拒
```

## 负责人决策（已确认，不得发明）

| 项 | 状态 |
|---|---|
| AMOUNT/加权、BUSINESS SLA、吉利联调 | 不推进 |
| iOS 条件执行器 / catalog 硬阻断翻转 | `BLOCKED_EXTERNAL`（需 Mac/Xcode） |
| 派单级 `supportedClientKinds` 过滤 | 需 Accepted 派单亲和规则 |
| Network Portal on-behalf 能力门禁 | 需 Accepted：`NETWORK_WEB` vs 代师傅 clientKind |
| REVIEW_TASK 模板分离 | 需 Accepted：taskId 绑定/触发/模板范围 |

## 下一（请负责人明示选项后再开工）

| 选项 | 说明 |
|---|---|
| A | iOS 条件执行器（换 Mac/Xcode 环境） |
| B | 派单过滤（先给 Accepted 规则或批准起草 Proposed ADR） |
| C | Network Portal on-behalf 门禁（先确认 clientKind 语义或批准起草 Proposed ADR） |
| D | REVIEW_TASK 模板分离（先给 Accepted 设计） |
| E | 其他模块/合并栈/文档收口（请点名） |

未选型前 **停止实施**，避免发明授权/派单/审核状态机规则。
