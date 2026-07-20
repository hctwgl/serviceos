---
title: M359 Feed/详情头客户端能力拒单
status: Implemented
milestone: M359
lastUpdated: 2026-07-19
relatedMilestones: [M356, M357, M358, M195, M243, M253]
openapiVersion: "1.0.53"
flywayVersion: "131"
---

# M359 Feed/详情头客户端能力拒单

## 目标

在 Technician Portal Feed 与任务详情入口对冻结 Bundle 的 FORM/EVIDENCE 做客户端能力预检，
避免师傅进入现场中途执行路径后才发现不兼容。

## 范围与非目标

- 范围：
  - `configuration::api` 公开 `FrozenBundleClientCapabilityProbe`；
  - 任务详情硬拒单：已知 `TECHNICIAN_WEB`/`TECHNICIAN_IOS` 不兼容 → 422
    `CLIENT_CAPABILITY_UNSUPPORTED`；
  - Feed ASSIGNMENT 项注解 `clientCapabilityUnsupportedDetail`（不隐藏任务、不整页拒单）；
  - OpenAPI 1.0.53；H5 Feed 展示说明并阻止进入不兼容详情；
  - readmodel `allowedDependencies` 增加 `configuration::api`；
  - 单元 + 控制器安全测试。
- 明确不做：
  - iOS 条件执行器 / 全量默认硬阻断（`form.condition.*` catalog 翻转）；
  - 派单/assignment 过滤；Bundle CANARY；
  - UNKNOWN 强制升级；整改路径专用头级门禁；
  - 8 态视觉 / admin-pilot；独立审核 HUMAN Task 模板；
  - Flyway 新迁移（仍为 131）。

## 事实源

- M356 发布门禁与能力目录
- M357 运行时门禁 `ClientCapabilityRuntimeGate`
- M358 `supportedClientKinds` 定向目标
- M195/M243 Technician Feed/详情
- M253 有界客户端元数据与 UNKNOWN 迁移窗口

## 设计要点

1. 预检复用运行时门禁与定向目标，不另造能力规则。
2. Feed 只注解、不删项：任务责任仍可见，客户端应展示并阻止进入详情执行。
3. 详情头硬失败关闭：禁止用空壳详情进入表单/资料路径。
4. `UNKNOWN`/非师傅端短路跳过（保留 M253 迁移窗口）。
5. 模块边界：configuration 拥有探针；readmodel 仅消费 `configuration::api`。

## 已实现

- `FrozenBundleClientCapabilityProbe` / `DefaultFrozenBundleClientCapabilityProbe`
- `DefaultTechnicianPortalQueryService` 详情 `requireCompatible`、Feed `findIncompatibilityDetail`
- Controller 传入 `ClientMetadata.KIND_ATTRIBUTE`
- OpenAPI 1.0.53：Feed 字段 + 详情 422
- H5 `TechnicianPortalTaskFeedPage` 展示并阻断深链
- `DefaultFrozenBundleClientCapabilityProbeTest`
- `TechnicianPortalControllerSecurityTest#taskDetailCapabilityUnsupportedReturns422`

## 明确未实现

- iOS 条件共用执行器与未声明时全量硬阻断；
- 派单级目标过滤；clientVersion 下限；
- 整改路径专用头级增强；动态 SupportedCapabilities 注册。

## 工程证据

| 类别 | 证据 |
|---|---|
| OpenAPI | 1.0.53；契约兼容门禁 |
| Flyway | 131（无新迁移） |
| 单元 | `DefaultFrozenBundleClientCapabilityProbeTest` |
| 安全 | `TechnicianPortalControllerSecurityTest` |
| IT | `TechnicianPortalFeedPostgresIT`（签名兼容） |
| ArchitectureTest | readmodel → configuration::api |
| H5 | Feed 能力说明与深链阻断 |

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultFrozenBundleClientCapabilityProbeTest,TechnicianPortalControllerSecurityTest
bash scripts/agent-verify.sh it TechnicianPortalFeedPostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
```
