---
title: M368 Network Portal on-behalf NETWORK_WEB 能力门禁
status: Implemented
milestone: M368
lastUpdated: 2026-07-20
relatedMilestones: [M201, M357, M361, M362, M367]
openapiVersion: "1.0.59"
flywayVersion: "134"
---

# M368 Network Portal on-behalf NETWORK_WEB 能力门禁

## 状态

**Implemented**。承接已接受 **ADR-089**（负责人选型：走网点端 `NETWORK_WEB`）。

## 目标

关闭 Network Portal 资料代补 / 整改代补路径上缺失的客户端能力门禁：按 **网点端
`NETWORK_WEB`** 校验冻结 EVIDENCE 槽位所需能力，禁止以代补绕过 `CLIENT_CAPABILITY_UNSUPPORTED`。

## 已实现范围

1. ADR-089 Accepted：on-behalf 能力判定 clientKind = `NETWORK_WEB`（非代师傅端）；
2. `ClientCapabilityCatalog` 登记 `NETWORK_WEB` 生产资料能力；RuntimeGate / Probe 强制范围包含之；
3. 定向 `supportedClientKinds` 对 `NETWORK_WEB` 跳过（仅约束师傅执行/派单）；
4. `DefaultNetworkPortalEvidenceService`：begin/finalize/snapshot/resubmit 要求
   `clientKind=NETWORK_WEB`，并对源 Task 解析槽位调用 `requireCompatibleEvidenceSlots`；
5. Controller 透传 `X-ServiceOS-Client-Kind`；OpenAPI **1.0.59** 登记 ClientKind 与 422；
6. 单元 + 控制器安全 + PostgreSQL IT（含 SIGNATURE 拒单、定向 TECHNICIAN_WEB 仍允许代补）。

## 明确未实现

- 表单代改 / Visit / 槽位 `allowOnBehalf` 策略表；
- iOS 条件执行器；clientVersion 下限；
- 要求配置资产声明 `NETWORK_WEB` 定向目标；
- 吉利联调 / AMOUNT / BUSINESS SLA。

## 验证

```bash
bash scripts/agent-verify.sh test DefaultClientCapabilityRuntimeGateTest,DefaultNetworkPortalEvidenceServiceTest,NetworkPortalEvidenceControllerSecurityTest
bash scripts/agent-verify.sh it NetworkPortalEvidenceOnBehalfPostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh docs
```
