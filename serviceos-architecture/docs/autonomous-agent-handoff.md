---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- master HEAD：`f3b623453a33ece91a691438b0c541e53c3282df`（PR #146 已合并）
- 工作分支：`cursor/bc-1e82b528-bdbd-41ef-98bb-900fb4d54a45-0eae`
- latestMilestone：**M296**
- Flyway：**117 / 119**
- OpenAPI：**1.0.39**
- 功能证据：`7b981191dd168c210484483d7443fea446e7ce73`
- 当前阶段：**P0 M296 稳定基线收口（执行中）** → 随后 **M297 Outbound Connector SPI**

## 已完成（用户批准主线）

阶段一～三核心切片已交付至 **M296**（已入 master）：

1. **阶段一**：多 OEM Connector SPI、条件/WAIT、家充模板、REFERENCE_OEM、双车企回归、接入手册
2. **阶段二**：PARALLEL/TIMER/SUB_PROCESS/多实例、取消重开跳转、补偿、标准服务模板
3. **阶段三 Phase K**：十大配置资产设计器 + 静态校验、依赖分析、干跑模拟、历史回放、Diff、审批、灰度、发布/停用/回滚

## PLAN 状态

用户已批准持续实施计划（P0～P4）。`/opt/cursor/artifacts/PLAN.md` 为当前执行权威；旧「完成/STOP」已撤销。

```text
P0 文档对齐 + 全量验证
→ P1 完整通用 Connector SPI（M297+）
→ P2 配置资产运行时引擎
→ P3 业务低代码设计器
→ P4 真实 OEM2（吉利；缺凭据则 BLOCKED_EXTERNAL）
```

## 已完成工作（本轮）

- 现场恢复：确认 master = `f3b62345`，无 open PR，无未提交工作
- 文档事实修正进行中（baselineCommit / handoff / roadmap / README / 集成导语）

## 未完成工作

- P0 全量 `verify-local.sh` 与内部失败修复
- M297+ Outbound/回调 Connector SPI
- 配置运行时引擎与低代码增强
- 吉利真实联调（缺 Sandbox）

## 设计决定

- 里程碑号按 `latestMilestone + 1` 领取；下一功能里程碑为 **M297**
- 禁止空 SPI 接口；每个切片必须有 BYD 归位或 REFERENCE 样本 + 测试
- 远端 `verify.yml` 已从 master 删除；完成门禁以本地全量验证为准
- 吉利 PDF 可作协议输入；无 Sandbox/凭据不得声称真实接入

## 阻塞（BLOCKED_EXTERNAL）

- Track F/G：iOS 离线、签名真机、TestFlight、生产 IdP、Swift/Xcode（本云环境无）
- 真实 OEM2/OEM3 Sandbox 与凭据（仓库有吉利/广汽 PDF，缺联调材料）
- 远端 GitHub Actions 全量 `verify` workflow（已删除）

## 下一步具体代码入口

1. 完成 P0 文档提交后：`sudo dockerd` → `bash scripts/verify-milestone-preflight.sh` → `bash scripts/verify-local.sh`
2. P0 收口后实施 M297：
   - `com.serviceos.integration.spi` 扩展 Outbound 端口
   - `BydReviewSubmissionTaskHandler` / `DefaultOutboundDeliveryService` 归位
   - `ArchitectureTest` + 相关 PostgresIT

## 验证入口（最近）

- `bash scripts/verify-milestone-preflight.sh`
- `bash scripts/verify-local.sh`（P0 候选门禁）
- 历史切片：`ConfigurationHistoricalReplayPostgresIT`、`DualOemInboundRegressionPostgresIT`、`ArchitectureTest`
