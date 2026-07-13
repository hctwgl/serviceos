---
title: 项目、配置、授权与审计治理 HTTP API
version: 0.1.0
status: Proposed
---

# 项目、配置、授权与审计治理 HTTP API

## 1. 目标

本文件补齐 Admin Portal 的项目、配置资产、角色授权和审计治理契约。所有写命令遵循 Idempotency-Key、If-Match、职责分离、增强审计和异步 operation 约定。

## 2. 项目与服务产品

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `GET /api/v1/projects` | 项目列表 | client、brand、status、period | 200 |
| `POST /api/v1/projects` | CreateProject | client、code、name、period、regions、owners | 201 |
| `GET /api/v1/projects/{id}` | 项目、版本、配置和负责人 | — | 200 |
| `POST /api/v1/projects/{id}:activate` | ActivateProject | effectiveAt、approvalRef | 200 |
| `POST /api/v1/projects/{id}:retire` | RetireProject | effectiveAt、reason、replacementRef? | 200 |
| `GET /api/v1/service-products` | 服务产品目录 | status、category | 200 |
| `POST /api/v1/service-products` | CreateServiceProduct | stableCode、name、category、description | 201 |
| `POST /api/v1/projects/{id}/service-products` | BindProjectServiceProduct | productRef、scope、effectiveWindow | 201 |

项目/服务产品稳定身份与可变运营配置分离。车企/品牌不创建专属核心表或代码分支。

## 3. 配置资产与草稿

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `GET /api/v1/configuration-assets` | 资产列表 | type、owner、project、status | 200 |
| `POST /api/v1/configuration-assets` | CreateConfigurationAsset | type、stableCode、name、owner、risk | 201 |
| `GET /api/v1/configuration-assets/{id}` | 元数据、版本、依赖、使用范围 | — | 200 |
| `POST /api/v1/configuration-assets/{id}/drafts` | CreateDraftRevision | baseVersionId?、changeReason | 201 |
| `GET /api/v1/configuration-drafts/{id}` | 草稿内容/Schema/验证摘要 | — | 200 |
| `PUT /api/v1/configuration-drafts/{id}` | SaveDraft | content、contentDigest、editSequence | 200 |
| `POST /api/v1/configuration-drafts/{id}:abandon` | AbandonDraft | reason | 200 |
| `GET /api/v1/configuration-asset-types/{type}/schema` | 编辑/验证 Schema | version? | 200 |

SaveDraft 只保存草稿；不能直接影响运行工单。服务端使用 asset type Schema 和安全限制校验内容大小/结构，不接受任意可执行代码。

## 4. 校验、样本回放与差异

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /api/v1/configuration-drafts/{id}:validate` | ValidateDraft | validationProfile、sampleRefs? | 202 |
| `POST /api/v1/configuration-drafts/{id}:replay` | ReplayDraft | sampleSetRef、comparisonBaseline | 202 |
| `GET /api/v1/configuration-validation-runs/{id}` | 规则、依赖、样本和错误 | — | 200 |
| `GET /api/v1/configuration-replay-runs/{id}` | 逐样本结果与差异 | — | 200 |
| `GET /api/v1/configuration-drafts/{id}/diff` | 与 base/published 的语义差异 | compareTo? | 200 |
| `POST /api/v1/configuration-drafts/{id}:preview-impact` | PreviewConfigurationImpact | targetScope、effectiveAt | 202 |

验证至少包含：Schema、引用、依赖 DAG、循环、缺失/多命中、规则覆盖/冲突、安全限制和历史样本。回放不产生真实通知、回传、派单或结算副作用。

## 5. 审批与发布

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /api/v1/configuration-drafts/{id}:submit-approval` | SubmitConfigurationApproval | validationRunId、replayRunId?、note | 202 |
| `GET /api/v1/configuration-approvals/{id}` | 审批链与决定 | — | 200 |
| `POST /api/v1/configuration-approvals/{id}:decide` | DecideConfigurationApproval | decision、note、conditions? | 200 |
| `POST /api/v1/configuration-release-candidates` | CreateReleaseCandidate | approvedDraftRefs、manifest、effectiveWindow | 201 |
| `GET /api/v1/configuration-release-candidates/{id}` | 候选 manifest、整组校验/回放/差异 | — | 200 |
| `POST /api/v1/configuration-release-candidates/{id}:submit-approval` | SubmitReleaseApproval | validationRefs、impactRef、note | 202 |
| `POST /api/v1/configuration-release-candidates/{id}:decide` | DecideReleaseApproval | decision、note、conditions? | 200 |
| `POST /api/v1/configuration-release-candidates/{id}:publish` | PublishConfigurationRelease | impactAcknowledgement、MFARef? | 202 |
| `GET /api/v1/configuration-releases/{id}` | manifest、versions、验证、状态 | — | 200 |
| `POST /api/v1/configuration-releases/{id}:stop-new-bindings` | StopNewBindings | reason、effectiveAt | 200 |
| `POST /api/v1/configuration-releases/{id}:replace` | ReplaceRelease | replacementReleaseId、effectiveAt、reason | 200 |

ReleaseCandidate 可继续整组校验/审批，但一旦审批后内容 digest 变化，审批失效。Publish 从已批准 candidate 原子生成不可变 PublishedVersions 与 ConfigurationRelease；失败不产生部分可绑定 release。已绑定工单不随 stop/replace 漂移；迁移走 MigrateConfiguration。

## 6. 配置解析与 Bundle 预览

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /api/v1/configuration-resolution:preview` | PreviewResolution | project/brand/product/region/businessDate/context | 200 |
| `GET /api/v1/configuration-bundles/{id}` | Bundle manifest 和版本 | — | 200 |
| `GET /api/v1/configuration-bundles/{id}/explanation` | 候选、命中/排除和解析器版本 | — | 200 |

Preview 不创建可被工单引用的 Bundle，除非明确 `purpose=CREATE_WORK_ORDER` 且由内部受信命令完成。多命中/零命中返回明确错误，不选择“最新一个”。

## 7. 组织、角色和能力查询

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/organizations` | 当前管理范围组织树/搜索 |
| `GET /api/v1/organizations/{id}` | 组织、上下级、成员摘要 |
| `GET /api/v1/security-principals` | 用户/服务主体查询 |
| `GET /api/v1/roles` | 角色模板与 capability |
| `GET /api/v1/capabilities` | 稳定能力目录和风险等级 |
| `GET /api/v1/data-scope-policies` | 范围策略目录/版本 |
| `GET /api/v1/field-policies` | 字段策略目录/版本 |

查询结果按管理范围过滤。完整 capability 目录不向普通外部用户暴露。

## 8. RoleGrant 与 Delegation

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `GET /api/v1/role-grants` | 授权查询 | principal、role、scope、activeAt | 200 |
| `POST /api/v1/role-grants` | RequestRoleGrant | principal、role、organization、scopePolicy、validity、reason | 202 |
| `POST /api/v1/role-grants/{id}:approve` | ApproveRoleGrant | decision、note | 200 |
| `POST /api/v1/role-grants/{id}:revoke` | RevokeRoleGrant | reason、effectiveAt | 200 |
| `POST /api/v1/delegations` | CreateDelegation | delegate、capabilities、scope、validity、reason | 201/202 |
| `POST /api/v1/delegations/{id}:revoke` | RevokeDelegation | reason | 200 |

授权和撤销只追加历史。申请人不能批准自己的高风险授权；授权不得超过批准人的可授予范围。离职/网点停用触发登录撤销和待办重新分配，不只删除菜单。

## 9. 授权解释

| 方法与路径 | 用途 |
|---|---|
| `POST /api/v1/authorization:explain` | 对管理/排障主体解释一次假设判定，不执行动作 |
| `GET /api/v1/resources/{type}/{id}/field-access` | 当前主体字段 HIDDEN/MASKED/READ/WRITE/EXPORT 摘要 |

Explain 需要专用能力并增强审计。它返回匹配 grant/policy/obligations 摘要，不泄露其他主体敏感授权或策略内部 secret。

## 10. 审计查询与导出

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/audit-records` | 按资源、主体、动作、时间、correlation 查询 |
| `GET /api/v1/audit-records/{id}` | 脱敏审计详情/完整性摘要 |
| `GET /api/v1/sensitive-access-audits` | 敏感查看/下载/导出审计 |
| `POST /api/v1/audit-exports` | 创建受控异步审计导出 |
| `POST /api/v1/audit-archives/{id}:verify` | 验证归档批次完整性 |

审计查询本身记录审计。导出使用字段策略、范围、purpose、审批、短时下载和撤销；普通应用 API 不提供删除/修改 AuditRecord。

## 11. 错误码

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `CONFIGURATION_DRAFT_VERSION_CONFLICT` | 409 | 草稿已被更新 |
| `CONFIGURATION_REFERENCE_INVALID` | 422 | 依赖版本不存在/不兼容 |
| `CONFIGURATION_DEPENDENCY_CYCLE` | 422 | 资产依赖成环 |
| `CONFIGURATION_VALIDATION_REQUIRED` | 409 | 缺少当前内容的有效校验 |
| `CONFIGURATION_APPROVAL_REQUIRED` | 403/409 | 审批/MFA/职责分离未满足 |
| `CONFIGURATION_RESOLUTION_NOT_UNIQUE` | 422 | 零命中或多命中 |
| `ROLE_GRANT_ESCALATION_FORBIDDEN` | 403 | 超过可授予范围 |
| `ROLE_GRANT_DUTY_CONFLICT` | 422 | 职责分离冲突 |
| `DELEGATION_SCOPE_TOO_BROAD` | 422 | 委托超过原授权 |
| `AUDIT_EXPORT_TOO_BROAD` | 422 | 范围/字段/保留策略不允许 |

## 12. 安全

- 配置内容、脚本/表达式、价格和连接器引用按敏感级别控制；
- 发布、价格、RoleGrant、敏感导出和 rollout 使用独立 capability/MFA/审批；
- 服务端不能信任前端传入的 owner、role、scope 或 approval 状态；
- 审批引用必须绑定精确内容 digest/version；草稿变化后旧审批失效；
- Preview/Replay 受 SideEffectFence 保护；
- 所有治理命令产生 AuditRecord 和不可变事件。
