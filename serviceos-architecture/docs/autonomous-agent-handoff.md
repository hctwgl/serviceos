---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- master HEAD（基线）：`f3b623453a33ece91a691438b0c541e53c3282df`（PR #146）
- 工作分支：`cursor/bc-1e82b528-bdbd-41ef-98bb-900fb4d54a45-0eae`
- 分支 HEAD：见最新 push（含 P0 修复）
- latestMilestone：**M296**（P0 收口中；下一功能里程碑 **M297**）
- Flyway：**117 / 119**
- OpenAPI：**1.0.39**
- 当前阶段：**P0 完成 → 开始 M297 Outbound Connector SPI**

## 已完成工作（本轮）

### P0 M296 稳定基线

1. 文档对齐：`baselineCommit`→合并 SHA；handoff/roadmap/README/集成导语；下一方向 P1～P4
2. PostgresIT Flyway 断言刷新 100/102 → 117/119
3. `ADMIN.CONFIGURATION.DESIGNER` 同步进 client-identities
4. `WorkflowLinearProgressionPostgresIT` 满足 `ck_wo_cancel_state`
5. TS client consumer 脚本：PATH 注入仓库锁定 `tsc`（npm pack prepare）

### 验证结果

| 门禁 | 结果 |
|---|---|
| `verify-milestone-preflight.sh` | PASS |
| `bash scripts/verify-local.sh`（L3 全量） | PASS（log `verify-20260719T014135-56812.log`，~486s） |
| `agent-verify.sh client-ts` | PASS |
| Admin Web `npm run build` | PASS |
| Client Identity TypeScript 部分 | PASS |
| Swift / iOS / swiftc | **BLOCKED_EXTERNAL** |
| 远端 `verify.yml` | **BLOCKED_EXTERNAL**（已从 master 删除） |
| 吉利/广汽 Sandbox 凭据 | **BLOCKED_EXTERNAL**（PDF 仅作协议输入） |

## PLAN 状态

执行中：P0 完成条件已满足（文档同步 + 内部全量验证通过 + 外部阻塞已登记）。下一步 M297。

## 未完成工作

- M297+ Outbound/回调 Connector SPI
- P2 配置运行时引擎
- P3 低代码设计器
- P4 吉利真实联调

## 下一步具体代码入口（M297）

1. 扩展 `com.serviceos.integration.spi`：Outbound 端口与 Failure/Ack 模型（有实现者）
2. 归位 `BydReviewSubmissionTaskHandler` / `DefaultOutboundDeliveryService`
3. `ArchitectureTest` + 既有 BYD outbound IT 行为保持
4. 实现文档 `architecture/310-m297-*.md` + 验收 `testing/294-m297-*-acceptance.md`

## 设计决定

- 禁止空 SPI；每个切片必须有 BYD 归位或 REFERENCE 样本
- 不以远端 CI 为完成条件
- 不得声称真实吉利已接入
