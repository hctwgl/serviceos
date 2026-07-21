---
title: M358 supportedClientKinds 定向发布
status: Implemented
milestone: M358
lastUpdated: 2026-07-19
relatedMilestones: [M356, M357, M286, M253]
openapiVersion: "1.0.52"
flywayVersion: "131"
---

# M358 supportedClientKinds 定向发布

## 目标

为 FORM/EVIDENCE 配置草稿提供显式 `supportedClientKinds` 定向发布目标，使 H5-only
（如 `visibleWhen`）成为可治理的发布声明；并在运行时对目标外客户端失败关闭。

## 范围与非目标

- 范围：
  - 草稿列 `supported_client_kinds` + 已发布侧表 `cfg_configuration_asset_client_target`（Flyway V131）；
  - 发布/校验门禁：未声明 = M356 默认；声明子集 = 并集阻断 + 子集内硬兼容；
  - 运行时：目标外 `TECHNICIAN_*` → `CLIENT_CAPABILITY_UNSUPPORTED`；
  - OpenAPI 1.0.52；Admin 设计器目标勾选；
  - 单元 + PostgreSQL IT。
- 明确不做：
  - Bundle CANARY / traffic_percent 轴变更；
  - Feed/详情头拒单（见 **M359**）；派单过滤见 **M366**；
  - iOS 条件执行器 / 全量默认硬阻断（未声明时仍为 M356 报告语义）；
  - UNKNOWN 强制升级；最低 clientVersion 策略表。

## 设计要点

1. **null/缺省** = 全部生产师傅端，保持 M356 并集阻断 + 分端报告。
2. **非空子集** = 仅对声明端评估；声明端缺口直接阻断。
3. 不参与授权；与 M286 Bundle 灰度正交。
4. 已发布目标侧表不可变（触发器拒绝 UPDATE/DELETE）。

## 已实现

- `ConfigurationClientCapabilityGate.evaluate(..., supportedClientKinds)`
- `JooqConfigurationDraftService` 持久化/发布侧表写入
- `ConfigurationAssetDefinition.supportedClientKinds` + Bundle 读取
- `DefaultClientCapabilityRuntimeGate` 目标外拒单
- Admin `ConfigurationDesignerPage` 定向目标控件
- `ConfigurationClientCapabilityGateTest` + runtime 单测 + `ClientCapabilityCompatGatePostgresIT`

## 明确未实现

- Feed 级目标过滤见 M359；派单级见 M366；clientVersion 下限；动态 SupportedCapabilities 注册。

## 验证命令

```bash
bash scripts/agent-verify.sh test ConfigurationClientCapabilityGateTest,DefaultClientCapabilityRuntimeGateTest
bash scripts/agent-verify.sh it ClientCapabilityCompatGatePostgresIT
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
bash scripts/agent-verify.sh arch
```
