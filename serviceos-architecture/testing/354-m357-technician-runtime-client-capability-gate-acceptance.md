---
title: M357 师傅端运行时客户端能力拒单验收矩阵
status: Implemented
milestone: M357
lastUpdated: 2026-07-19
---

# M357 师傅端运行时客户端能力拒单验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M357-RT-001 | P0 | H5 加载含 visibleWhen 的冻结表单 | 200，可继续填写 | `DefaultClientCapabilityRuntimeGateTest#webAcceptsVisibleWhenForm` |
| M357-RT-002 | P0 | iOS 加载含 visibleWhen 的冻结表单 | 422 `CLIENT_CAPABILITY_UNSUPPORTED`，中文 detail 含能力编码 | `DefaultClientCapabilityRuntimeGateTest#iosRejectsVisibleWhenForm` |
| M357-RT-003 | P0 | iOS/H5 加载 SIGNATURE 资料槽位 | 422 | `DefaultClientCapabilityRuntimeGateTest#iosRejectsSignatureEvidenceSlot` |
| M357-RT-004 | P0 | UNKNOWN clientKind | 跳过运行时门禁（M253 迁移） | `DefaultClientCapabilityRuntimeGateTest#unknownClientSkipsEnforcement` |
| M357-RT-005 | P0 | 表单提交前复检 | 不兼容禁止提交 | `DefaultTechnicianFormService` + Service Test |
| M357-RT-006 | P0 | OpenAPI 1.0.51 | forms/evidence 登记 422；client-ts 可生成 | contracts + client-ts |
| M357-RT-007 | P1 | H5 展示服务端 detail | `CLIENT_CAPABILITY_UNSUPPORTED` 文案可见 | technician-web `client.ts` |
| M357-RT-008 | P0 | ArchitectureTest | 模块边界通过 | arch |

## 明确不在本矩阵

- 灰度定向发布；强制 Header；iOS 条件执行器；Feed/详情头拒单。
