---
title: M361 整改路径客户端能力门禁对齐
status: Implemented
milestone: M361
lastUpdated: 2026-07-19
relatedMilestones: [M357, M358, M359, M266]
openapiVersion: "1.0.54"
flywayVersion: "131"
---

# M361 整改路径客户端能力门禁对齐

## 目标

关闭 M357/M359 遗留的「整改路径能力门禁旁路」：Technician 整改资料读写与主 Evidence
路径使用同一 `ClientCapabilityRuntimeGate` / 定向目标语义，禁止以 correction 绕过
`CLIENT_CAPABILITY_UNSUPPORTED`。

## 范围与非目标

- 范围：
  - `TechnicianCorrectionService` 资料路径透传 `clientKind`；
  - listSlots / listItems / beginUpload / finalizeUpload / createSnapshot / resubmit 复检；
  - 源 Task 冻结 Bundle EVIDENCE `supportedClientKinds`；
  - OpenAPI 1.0.54 登记 422；
  - 单元 + 控制器安全测试；
  - H5 已有 `userFacingError` 展示（无新 UI）。
- 明确不做：
  - iOS 条件执行器 / 全量硬阻断；
  - 派单过滤；REVIEW_TASK 模板分离；
  - UNKNOWN 强制升级；Flyway 新迁移；
  - Network Portal on-behalf 整改能力门禁（非本切片）。

## 设计要点

1. 复用 M357/M358 门禁与定向目标，不另造规则。
2. 校验对象是**源业务 Task** 的槽位与冻结 Bundle（整改资料写在源 Task 上）。
3. claim/start 不涉及资料定义，不做能力门禁。
4. resubmit 复检，防止上传时兼容、重提时换端绕过。

## 已实现

- `DefaultTechnicianCorrectionService` + Controller `ClientMetadata.KIND_ATTRIBUTE`
- OpenAPI 1.0.54 整改资料路径 422
- `DefaultTechnicianCorrectionServiceTest` / `TechnicianCorrectionControllerSecurityTest`

## 明确未实现

- iOS 条件执行器；派单过滤；独立审核 HUMAN Task 模板分离。

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultTechnicianCorrectionServiceTest,TechnicianCorrectionControllerSecurityTest
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
```
