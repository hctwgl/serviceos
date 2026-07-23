---
title: ADR-089：Network Portal on-behalf 能力门禁采用 NETWORK_WEB
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-039-network-portal-evidence-on-behalf.md
  - decisions/ADR-088-dispatch-supported-client-kinds-filter.md
---

# ADR-089：Network Portal on-behalf 能力门禁采用 NETWORK_WEB

## 1. 状态与已接受决策

负责人确认（选型原文：`走网点端 NETWORK_WEB`）后正式接受：

1. Network Portal 资料代补 / 整改代补（on-behalf）的**客户端能力门禁**按
   `X-ServiceOS-Client-Kind=NETWORK_WEB` 评估，**不得**冒充或代用师傅端
   `TECHNICIAN_WEB` / `TECHNICIAN_IOS` 作为能力判定 clientKind；
2. 缺失、非法或非 `NETWORK_WEB` 的 ClientKind（含 `UNKNOWN`）在 on-behalf 写路径上
   **失败关闭**，返回 `CLIENT_CAPABILITY_UNSUPPORTED`（HTTP 422）；
3. 对源业务 Task 已解析 EVIDENCE 槽位调用 `ClientCapabilityRuntimeGate`，校验
   `NETWORK_WEB` 静态能力目录是否覆盖所需 mediaType / `requiredWhen` 等能力码；
4. 资产级 `supportedClientKinds` **定向发布目标**仅约束师傅执行与派单过滤；
   on-behalf **不要求** `NETWORK_WEB ∈ supportedClientKinds`。网点代补权威来自
   ACTIVE NetworkMembership + NETWORK scope `evidence.submitOnBehalf` + ACTIVE NETWORK 责任
   （ADR-039），不得用师傅定向目标旁路或阻断合法代补；
5. `NETWORK_WEB` 纳入 RuntimeGate / Probe 的可强制 clientKind 集合；能力目录登记与当前
   Network Web 已验收的在线资料能力一致（基础 PHOTO/VIDEO/DOCUMENT 与条件 requiredWhen）。

## 2. 上下文

师傅端已建立 ClientCapability 门禁，但 RuntimeGate 故意不对
`NETWORK_WEB` 强制，导致 Network Portal on-behalf 可在配置能力不兼容时仍写入。
ADR-088 将 on-behalf / `NETWORK_WEB` 代师傅语义列为另案。本 ADR 闭合该选型，不改变
ADR-039 的授权、责任与 CaptureMetadata 语义。

## 3. 后果

- 当前实现：Controller 透传 ClientKind；Portal 适配器硬校验 `NETWORK_WEB` +
  槽位能力门禁；OpenAPI 登记 422；无新 Flyway；
- 不接受：用师傅声明 clientKind 作代补门禁、要求配置资产声明 `NETWORK_WEB` 定向目标、
  表单代改、Visit、iOS 条件执行器变更。
