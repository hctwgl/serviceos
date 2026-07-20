---
title: M356 客户端能力兼容发布门禁
status: Implemented
milestone: M356
lastUpdated: 2026-07-19
relatedMilestones: [M253, M252, M349, M350, M283]
openapiVersion: "1.0.50"
flywayVersion: "130"
---

# M356 客户端能力兼容发布门禁

## 目标

在 FORM/EVIDENCE 配置校验与发布路径上建立服务端权威的客户端能力兼容门禁，
阻止将当前生产师傅端（H5/iOS）均无法执行的字段类型或资料类型全量发布；
并为设计器返回分客户端兼容性报告，避免静默忽略。

## 范围与非目标

- 范围：
  - 静态 `ClientCapabilityCatalog`（`TECHNICIAN_WEB` / `TECHNICIAN_IOS` 当前基线能力）；
  - FORM/EVIDENCE 定义所需能力提取；
  - `validate` / `publish` 失败关闭：全客户端均不支持的能力禁止进入 `VALIDATED`/`PUBLISHED`；
  - OpenAPI `ConfigurationDraft.clientCompatibility` 报告；
  - Admin 配置设计器展示兼容性报告；
  - 发布/校验审计记录能力判断摘要；
  - 单元测试 + PostgreSQL IT。
- 明确不做：
  - 灰度通道 / `supportedClientKinds` 定向发布；
  - 强制升级策略与最低 runtime 版本策略表持久化；
  - 任务详情运行时 Problem Details 拒单；
  - iOS 条件表达式共用执行器补齐；
  - `editableWhen` / `defaultExpression` / 远程选项（仍由既有 schema 门禁拒绝）；
  - Android / Consumer / Admin 作为履约执行客户端；
  - Flyway 新表（本切片使用代码内静态目录）。

## 事实源

- `product/08-technician-multi-client-strategy.md` §8 版本兼容
- `architecture/266-m253-client-metadata-observability.md`
- `architecture/265-m252-cross-client-identity-registry.md`
- M349/M350 Technician H5 条件/规则执行器基线
- 当前 H5/iOS 在线表单支持的标量字段集合

## 设计要点

1. **能力编码**使用稳定英文标识（如 `form.fieldType.ENUM`、`evidence.mediaType.SIGNATURE`），
   面向用户的说明使用中文写入报告。
2. **阻断规则**：所需能力在 `TECHNICIAN_WEB` 与 `TECHNICIAN_IOS` 的并集中均不存在 → 校验失败。
3. **分客户端报告**：某客户端缺少条件/规则能力时标记 `compatible=false` 并列出缺口，
   但不单独阻断（待灰度通道与 iOS 共用执行器后升级为全量发布硬门禁）。
4. **不参与授权**：沿用 M253，clientKind/Version 与能力目录不授予权限。
5. **历史工单**：既有已发布资产与 Bundle 锁定不受影响；门禁只作用于新草稿校验/发布。

## 已实现

- `ConfigurationClientCapabilityGate` / `ClientCapabilityCatalog` / Analyzer
- `DefaultConfigurationDraftService.validate/publish` 接入门禁与审计
- OpenAPI 1.0.50 `clientCompatibility`
- Admin `ConfigurationDesignerPage` 兼容报告面板
- `ConfigurationClientCapabilityGateTest`（5）
- `ClientCapabilityCompatGatePostgresIT`（4）
- 既有 `FormEvidenceSlaDesignerPostgresIT` 回归通过

## 明确未实现

- 仅发布到兼容客户端 / 灰度 cohort（定向 `supportedClientKinds` 见 **M358**）；
- 任务分配与详情头级运行时能力拒单（表单/资料路径运行时拒单见 **M357**）；
- 条件类能力对 iOS 的全量发布硬阻断；
- 客户端主动上报 SupportedCapabilities[] 动态注册；
- Admin 兼容性可视化设计器增强以外的运营面板。

## 工程证据

| 类别 | 证据 |
|---|---|
| OpenAPI | 1.0.50；契约兼容门禁通过 |
| Flyway | 130（无新迁移） |
| 单元 | `ConfigurationClientCapabilityGateTest` |
| PostgreSQL IT | `ClientCapabilityCompatGatePostgresIT` + `FormEvidenceSlaDesignerPostgresIT` |
| ArchitectureTest | 通过 |
| Admin | `npm run build` / `npm run test:unit` |
| Client | `bash scripts/agent-verify.sh client-ts` |

## 验证命令

```bash
bash scripts/agent-verify.sh test ConfigurationClientCapabilityGateTest
bash scripts/agent-verify.sh it FormEvidenceSlaDesignerPostgresIT,ClientCapabilityCompatGatePostgresIT
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
bash scripts/agent-verify.sh arch
cd serviceos-admin-web && npm ci && npm run build && npm run test:unit
```
