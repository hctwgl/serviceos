---
title: M299 出站提审 Profile 注册表
status: Implemented
milestone: M299
lastUpdated: 2026-07-19
relatedMilestones: [M58, M297, M298]
---

# M299 出站提审 Profile 注册表

## 目标

将 `DefaultOutboundDeliveryService` / 完成落账中的 BYD 硬编码常量收敛为可注册
`OutboundReviewSubmissionProfile`，按入站 lineage 或 connectorVersion 唯一解析。

## 范围与非目标

- 范围：Profile SPI、注册表零/多命中失败关闭、BYD Profile、创建与技术接受落账改用注册表。
- 明确不做：Update/Cancel、远端查询、ExternalReviewRouteService 全面去硬编码、OpenAPI/Flyway。

## 已实现

- `OutboundReviewSubmissionProfile` + `OutboundReviewSubmissionProfiles`
- `BydOutboundReviewSubmissionProfile`
- create/replay/finalize 路径解析 Profile
- 单元测试 + ArchitectureTest + Outbound/Review IT 回归

## 明确未实现

- Route 服务 connector 注册表化；多 OEM 出站创建样本。

## 验证命令

```bash
bash scripts/agent-verify.sh test ArchitectureTest,OutboundReviewSubmissionProfilesTest
bash scripts/agent-verify.sh it OutboundDeliveryQueuePostgresIT,ReviewCasePostgresIT
```
