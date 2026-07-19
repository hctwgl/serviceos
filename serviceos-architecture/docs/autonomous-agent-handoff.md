---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/147
- master 基线：`f3b623453a33ece91a691438b0c541e53c3282df`（M296）
- 工作分支：`cursor/bc-1e82b528-bdbd-41ef-98bb-900fb4d54a45-0eae`
- latestMilestone：**M297**
- baselineCommit（功能证据）：`2fb79020765153741b48cc02860fb7aacecc2ea7`
- Flyway：**117 / 119**
- OpenAPI：**1.0.39**
- 当前阶段：**P1 续 — 下一刀 M298 审核回调 SPI**

## 已完成

### P0 M296 稳定基线

- 文档对齐；Flyway IT 断言 117/119；client-identities 补 `ADMIN.CONFIGURATION.DESIGNER`
- workflow cancel IT 满足 `ck_wo_cancel_state`；TS client pack PATH 修复
- `bash scripts/verify-local.sh` PASS（`verify-20260719T014135-56812.log`）
- client-ts PASS；Admin build PASS

### M297 出站提审 Connector SPI

- SPI：`OutboundSubmissionConnector` + 请求/签名/传输/技术 ACK/`ConnectorFailure`
- `OutboundSubmissionPipeline`；`BydOutboundSubmissionConnector`；薄 TaskHandler
- ADR-086；实现/验收文档；ArchitectureTest + 单元测试 + OutboundDeliveryQueuePostgresIT

## 验证（最近）

```text
bash scripts/verify-milestone-preflight.sh          PASS (M297)
bash scripts/agent-verify.sh test ArchitectureTest,OutboundSubmissionSpiTest,BydOutboundSubmissionConnectorTest  PASS
bash scripts/agent-verify.sh it OutboundDeliveryQueuePostgresIT  PASS
bash scripts/verify-local.sh                         PASS (P0 全量；M297 后再跑阶段门禁)
```

## BLOCKED_EXTERNAL

- Swift/Xcode/swiftc、签名真机、TestFlight、生产 IdP
- 吉利/广汽 Sandbox 凭据（PDF 仅协议输入）
- 远端 `verify.yml`（已从 master 删除）

## 下一步具体代码入口（M298）

1. 设计审核回调 SPI：`mapInboundReviewCallback` / 通用回调管道
2. 归位 `BydCpimReviewCallbackService`（验签/防重放/路由/部分成功保持）
3. 重复、乱序、冲突失败关闭 IT
4. 文档 `architecture/311-m298-*` + `testing/295-m298-*`

## 设计决定

- 技术 ACK ≠ 业务 ACK；UNKNOWN ≠ RETRYABLE
- 禁止空 SPI；OEM 不写领域表
- 创建面 `DefaultOutboundDeliveryService` BYD 常量注册表化留后续切片
