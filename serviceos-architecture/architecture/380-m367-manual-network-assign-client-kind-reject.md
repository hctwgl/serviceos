---
title: M367 Manual/Network assign supportedClientKinds 硬拒绝
status: Implemented
milestone: M367
lastUpdated: 2026-07-20
relatedMilestones: [M366, M358, M196, M200, M144]
openapiVersion: "1.0.58"
flywayVersion: "134"
---

# M367 Manual/Network assign supportedClientKinds 硬拒绝

## 状态

**Implemented**。承接已接受 ADR-088 的 **A1-B** 切片（在 M366 A1-R～A5-R 之上）。

## 目标

Admin Manual 与 Network Portal assign/reassign 在激活 TECHNICIAN 前，按冻结 Bundle
FORM∩EVIDENCE 定向目标硬校验师傅声明；不兼容则 **422** + DENY 审计，避免运营覆盖绕过
M366 自动池过滤。

## 已实现范围

1. `ManualTechnicianClientKindGate`：复用 `resolveDispatchTargetClientKinds` + 师傅声明；
2. `JooqManualServiceAssignmentService.manualAssign` / `reassignTechnician` 激活前调用
   （Network Portal 路径委托 Manual，一并覆盖）；
3. 不兼容 → `CLIENT_CAPABILITY_UNSUPPORTED` + 审计
   `SERVICE_DISPATCH_TECHNICIAN_CLIENT_KIND_REJECT` / `error_code=CLIENT_KIND_INCOMPATIBLE`；
4. 资产全未定向 → 不拦截（与 M366 一致）；
5. `TechnicianDeclaredClientKindsQuery`（network::api）供派单只读声明。

## 明确未实现

- on-behalf / `NETWORK_WEB` 代师傅语义；
- iOS 条件执行器；clientVersion 下限；
- 删除执行门禁（A5-R 仍保留）。

## 验证

```bash
bash scripts/agent-verify.sh test DispatchClientKindMatchTest
bash scripts/agent-verify.sh it ManualAssignClientKindRejectPostgresIT
bash scripts/agent-verify.sh it ManualServiceAssignmentPostgresIT,NetworkPortalAssignTechnicianPostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh docs
```
