---
title: M357 师傅端运行时客户端能力拒单
status: Implemented
milestone: M357
lastUpdated: 2026-07-19
relatedMilestones: [M356, M253, M263, M264]
openapiVersion: "1.0.51"
flywayVersion: "130"
---

# M357 师傅端运行时客户端能力拒单

## 目标

在师傅端表单/资料读写路径上，对已知 `TECHNICIAN_WEB`/`TECHNICIAN_IOS` 权威校验冻结配置所需能力；
不兼容时返回 Problem Details `CLIENT_CAPABILITY_UNSUPPORTED`，禁止静默忽略。

## 范围与非目标

- 范围：
  - `configuration::api` 公开 `ClientCapabilityRuntimeGate`；
  - Technician forms GET/POST、evidence slots/items/upload/finalize/snapshot、task complete 复检；
  - OpenAPI 1.0.51 登记 422；
  - H5 展示服务端权威中文 detail；
  - 单元测试 + 控制器安全测试更新。
- 明确不做：
  - 灰度/`supportedClientKinds` 定向发布；
  - 强制 Header（UNKNOWN 仍跳过，保留 M253 迁移窗口）；
  - iOS 条件执行器补齐；
  - 任务 Feed/详情头本身拒单；
  - 整改路径专用门禁增强；
  - Flyway 新表。

## 事实源

- M356 发布门禁与能力目录/提取器
- `product/08-technician-multi-client-strategy.md` §8
- M253 有界客户端元数据与 UNKNOWN 迁移窗口
- M263/M264 Technician 表单/资料 Portal 适配层

## 设计要点

1. 复用 M356 能力目录与提取器，但按**单一 clientKind** 失败关闭（与发布并集规则不同）。
2. 不参与授权；Portal Context / assignment 门禁仍先行。
3. 历史已绑定 Bundle 不受发布门禁回写影响；运行时按冻结定义判断当前客户端是否可执行。
4. `UNKNOWN` 与非师傅端跳过强制门禁，不得借此绕过已知 `TECHNICIAN_*` 判断。

## 已实现

- `ClientCapabilityRuntimeGate` / `DefaultClientCapabilityRuntimeGate`
- `DefaultTechnicianFormService` list/submit 接入
- `DefaultTechnicianEvidenceService` slots/items/upload/finalize/snapshot/complete 接入
- `ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED`
- OpenAPI 1.0.51 相关 422
- H5 `userFacingError` 展示服务端 detail
- `DefaultClientCapabilityRuntimeGateTest`（4）
- 表单/资料服务与控制器安全测试更新

## 明确未实现

- 灰度通道 / `supportedClientKinds`（定向发布见 **M358**）；
- UNKNOWN 强制升级策略；
- iOS 条件共用执行器与全量发布硬阻断；
- Feed/任务详情头级拒单；
- 整改路径专用能力门禁增强。

## 工程证据

| 类别 | 证据 |
|---|---|
| OpenAPI | 1.0.51；契约兼容门禁通过 |
| Flyway | 130（无新迁移） |
| 单元 | `DefaultClientCapabilityRuntimeGateTest` + Form/Evidence Service |
| 安全 | Technician Form/Evidence/Completion Controller Security |
| ArchitectureTest | 通过 |
| Client | `bash scripts/agent-verify.sh client-ts` |
| H5 | `serviceos-technician-web/src/api/client.ts` |

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultClientCapabilityRuntimeGateTest,DefaultTechnicianFormServiceTest,DefaultTechnicianEvidenceServiceTest,TechnicianFormControllerSecurityTest,TechnicianEvidenceControllerSecurityTest,TechnicianTaskCompletionControllerSecurityTest
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
```
