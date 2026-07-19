---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- 分支：`cursor/m321-integration-mapping-mainpath-88d5`（自 `master`）
- `master` HEAD：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 **已合并**）
- latestMilestone（master）：**M320**；本分支推进 **M321**
- Flyway：**120 / 122**（M321 无新迁移）；OpenAPI：**1.0.43**
- 阶段：Configuration-Driven Fulfillment Runtime — P0 基线收口 + P1 入站 Mapping 物化

## 已完成（本回合起点前）

- M320 三 OEM 并行建单冒烟已合入 master
- `verify-local.sh` 阶段门禁曾通过（日志 `target/verification-logs/verify-20260719T030447-406231.log`）
- P0 文档漂移修复进行中：`baselineCommit`→合并提交、handoff 标记 PR #147 已合并、README/status 下一方向改为配置驱动履约

## 进行中

- **M321**：冻结 INBOUND INTEGRATION Mapping 物化为建单 Canonical/领域命令（替代仅校验丢弃）
- Mapping 命中字段权威覆盖 OEM 兼容 Mapper；`mappingVersionId`=`assetVersionId`；Canonical 嵌入 `contentDigest`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步入口

1. 完成 M321 代码 + 单元/PostgreSQL IT + 里程碑文档
2. 精准验证后提交并开 Draft PR
3. 随后 M322+：出站 Mapping / ASSIGNEE 自动分配（按优先级）

## 验证

```text
# P0 文档：bash scripts/agent-verify.sh docs
# M321：bash scripts/agent-verify.sh test CreateWorkOrderMappingMaterializerTest,DefaultIntegrationMappingRuntimeTest
# M321 IT：bash scripts/agent-verify.sh it BydCpimInboundOrderHttpPostgresIT,IntegrationMappingRuntimePostgresIT
```
