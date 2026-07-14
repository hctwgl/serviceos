---
title: ServiceOS 实施状态总览
version: 0.1.0
status: Implemented
lastUpdated: 2026-07-14
baselineCommit: a328755f69c09291841f2bbb6767c28b814de821
latestMilestone: M49
---

# ServiceOS 实施状态总览

本文件是 ServiceOS 面向项目负责人、开发者和 Agent 的统一实施进度入口，用于回答：

1. 当前已经实施了什么；
2. 每项能力由哪些代码、迁移、契约和测试证明；
3. 哪些能力只完成了部分纵向切片；
4. 哪些能力仍停留在设计阶段；
5. 下一阶段应从哪里继续。

本文件不替代架构设计、里程碑实现文档和验收矩阵。发生冲突时，以已接受 ADR、机器契约、测试证据和对应里程碑文档为准。

## 1. 状态定义

| 状态 | 含义 |
|---|---|
| `IMPLEMENTED` | 已有代码、数据库迁移、机器契约和适用自动化验收证据 |
| `PARTIAL` | 已完成一个或多个可靠纵向切片，但该业务能力整体尚未闭环 |
| `ACCEPTED` | 设计已经接受，可指导实现，但尚无完整工程证据 |
| `PROPOSED` | 已形成可评审设计，但尚未被接受或实施 |
| `BLOCKED` | 依赖外部业务确认、ADR 或基础设施决定，暂不能可靠实施 |

注意：

- `Accepted` 不等于已经开发；
- `Implemented` 只表示对应里程碑声明的范围已经实现，不代表整个领域完成；
- 判断完成范围时必须同时阅读实现文档中的“明确未实现”和对应验收矩阵。

## 2. 当前基线

| 项目 | 当前值 |
|---|---|
| 最新实施里程碑 | M49 ExternalReviewReceipt |
| 基线提交 | `a328755f69c09291841f2bbb6767c28b814de821` |
| 后端形态 | Java 21 + Spring Boot + Spring Modulith 模块化单体 |
| 当前可构建工程 | `serviceos-backend`、`serviceos-contracts` |
| 前端工程 | 尚未建立；已有 Admin、Network、Technician 产品与交互规格 |
| 数据库 | PostgreSQL + Flyway（当前版本 049 / 51） |
| 契约 | OpenAPI 0.24.0 + 事件 JSON Schema |

每次完成新里程碑时，Agent 必须更新本节的最新里程碑、基线提交和更新时间。

## 3. 能力实施总览

| 领域 | 能力 | 状态 | 已完成范围 | 主要未完成范围 | 最近证据 |
|---|---|---|---|---|---|
| 工程基础 | 构建、测试、契约、可观测性、容器发布 | `IMPLEMENTED` | Maven、PostgreSQL IT、契约门禁、Trace/指标、单镜像迁移和回滚演练 | 正式 K8s、多故障域、PITR、SBOM/签名、正式 Secret Manager | M8～M14 |
| 身份授权 | OIDC/JWT、Capability、Tenant/Project Scope、拒绝审计 | `IMPLEMENTED` | 后端认证授权和范围校验基线 | 正式企业 IdP、完整组织治理 UI | M9 |
| 可靠消息 | Inbox、Outbox、Worker claim/lease/retry | `IMPLEMENTED` | 本地可靠发布消费、恢复和人工接管基础 | 正式 Broker 和跨服务运行 | M9～M10 |
| 配置中心 | 不可变配置资产、Bundle 发布和版本锁定 | `PARTIAL` | FORM、EVIDENCE 等资产发布基础和工单/任务冻结引用 | ADR-018 表达式运行时、完整审批和依赖闭包 | M16、M33、M36 |
| 外部接入 | BYD CPIM V7.3.1 入站安全与工单接入 | `PARTIAL` | 验签、防重放、映射、幂等收单、配置锁定 | 全量车企接口、完整回传和正式生产确认项 | M16 |
| 工单 | WorkOrder 接收、激活、履约完成 | `IMPLEMENTED` | 权威工单、工作流启动、跨阶段和 END 完结 | 完整取消、暂停、恢复和全部业务分支 | M16～M19 |
| 工作流 | 线性 Stage/Task 运行时 | `PARTIAL` | 精确版本启动、线性推进、唯一跨阶段推进、完成事件 | 并行/汇聚网关、完整条件表达式和复杂流程语义 | M17～M19 |
| 人工任务 | claim/start/complete、责任和执行保护 | `IMPLEMENTED` | 人工命令、候选领取、唯一责任、release/reclaim、执行保护；表单、资料和双引用完成门禁 | Review 完成条件 | M20～M23、M35、M41、M43 |
| 服务分配 | 网点分配、容量、改派 Saga、超时恢复 | `IMPLEMENTED` | ServiceAssignment、容量权威、改派、终止、对账和自动恢复 | 完整策略评分、全部异常分支和 UI | M24～M28 |
| 运营异常 | 异常工作台基础 | `PARTIAL` | 异常记录和恢复入口基础 | 完整通知、运营中心前端和跨域异常目录 | M29 |
| 预约 | 预约修订、联系终态动作 | `PARTIAL` | Revision、并发和终态动作基础 | 用户确认渠道、完整日程和跨端协作 | M30～M31 |
| 现场作业 | Visit 生命周期 | `PARTIAL` | Visit 运行时基础 | GPS 策略、完整现场提交、离线同步和师傅端 | M32 |
| 动态表单 | 资产、冻结版本、不可变提交和 Task 完成门禁 | `PARTIAL` | 固定 required、基础类型校验、精确版本提交和完成引用 | 条件表达式、复杂 validator、草稿、冲突、更正和审核 | M33～M35 |
| 资料 Evidence | 资产、槽位、Item/Revision、机器校验、Snapshot、完成门禁、作废、Review、Correction | `PARTIAL` | 固定槽位、安全文件 Finalize/Invalidate 联动、确定性机器校验、TASK_SUBMISSION Snapshot、完成门禁、作废、Review/Correction/整改 Task、强制通过/重开 | 条件槽位、OCR/CV | M36～M49 |
| 安全文件 | Begin/Finalize/隔离/扫描/授权下载/作废 | `IMPLEMENTED` | 独立安全文件生命周期；Evidence 编排 Begin/Finalize/Invalidate 联动 | 正式对象存储、专业扫描服务、物理删除 | M11、M38、M46 |
| 审核整改 | ReviewCase、ReviewDecision、CorrectionCase | `PARTIAL` | Review + Correction + 整改 Task + 强制通过/重开 + 车企回执；补传轮次只追加 | 自动指派、前端、CLIENT Case 自动创建 | M44～M49 |
| SLA | 时钟、预警、升级 | `PROPOSED` | 已有总体设计 | 完整运行时和验收尚未实施 | `architecture/12-*` |
| 通知 | 通知与运营异常中心 | `PROPOSED` | 已有总体设计 | 通知通道、模板、可靠发送和 UI | `architecture/14-*` |
| 履约事实与试算 | 事实提取和双向试算 | `PROPOSED` | 已有设计、API 和数据规划 | 运行时、投影和前端工作区 | M5 设计 |
| 对账结算 | 对账、结算、争议与调整 | `PROPOSED` | 已有边界设计 | 正式运行时和页面 | `architecture/16-*` |
| Admin Portal | 总部运营后台 | `PROPOSED` | 信息架构、Page ID、路由、权限和页面规格 | 前端代码、设计系统实现和 E2E | M7 设计 |
| Network Portal | 网点协作端 | `PROPOSED` | 页面和跨端协作规格 | 前端代码和 E2E | M7 设计 |
| Technician App | 师傅移动端 | `PROPOSED` | 弱网、离线工作包、上传队列和页面规格 | 移动端工程、真机和离线运行时 | M7 设计 |
| External Portal | 用户/车企受控页面 | `PROPOSED` | 最小边界规划 | 二期页面和工程实现 | M7 设计 |

## 4. 最近里程碑

### M33～M35：动态表单

已实现：

- FORM 配置资产发布基础；
- Task 冻结精确 FormVersion；
- 不可变 FormSubmission；
- 固定 required 和基础类型验证；
- Task 完成只接受同 Task、同项目、同冻结版本的有效提交。

未实现：

- ADR-018 条件表达式；
- 复杂 validator；
- 草稿、预填冲突和更正；
- 表单审核闭环。

### M36～M49：Evidence

已实现：

- EVIDENCE 资产发布门禁与固定 EvidenceSlot 解析（M36～M37）；
- EvidenceItem / 不可变 EvidenceRevision 与安全文件 Begin/Finalize（M38）；
- 确定性机器校验与 VALIDATED / VALIDATION_FAILED（M39）；
- 不可变 EvidenceSetSnapshot（TASK_SUBMISSION）（M40）；
- 无 formRef 资料 Task 完成仅接受精确 Snapshot 引用与 digest（M41）；
- 授权 `VALIDATED → INVALIDATED`、槽位投影刷新、历史 Snapshot 不可改写（M42）。
- formRef 且非空 EvidenceSlot Task 以精确 FormSubmission 和 TASK_SUBMISSION Snapshot
  的 `inputVersionRefs` 双引用完成，并同事务持久化（M43）；
- ReviewCase 绑定 TASK_SUBMISSION Snapshot；只追加 APPROVED/REJECTED ReviewDecision（M44）；
- REJECTED 同事务创建 CorrectionCase；补传轮次只追加；RESUBMITTED→CLOSED（M45）；
- Evidence invalidate 同事务联动 StoredFile AVAILABLE→INVALIDATED（M46）；
- CorrectionCase 打开时自动创建 evidence.correction Task 并进入 IN_PROGRESS（M47）；
- OPEN 强制通过 FORCE_APPROVED；APPROVED/FORCE_APPROVED 重开产生新 OPEN 案例（M48）；
- 适配层记录 ExternalReviewReceipt 并追加 EXTERNAL 决定；驳回开客服协调 Task（M49）。

未实现：

- `requiredWhen` 条件解析和可审计重解析；
- OCR / 图像 CV / GPS 权威距离；
- 整改候选人自动指派。

## 5. 下一实施方向

在没有更新事实源或新批准决策的情况下，建议下一可靠纵向切片是：

```text
M50 条件槽位 requiredWhen（需接受 ADR-018）或整改候选人自动指派
```

接手 Agent 必须先检查仓库是否已有更新的里程碑文档、ADR 或提交。

## 6. 证据阅读方法

判断某项能力是否完成时，按以下顺序检查：

```text
本文件中的状态
→ 对应 architecture/Mxx 实现文档
→ 对应 testing/Mxx 验收矩阵
→ implementation-traceability-matrix.md
→ OpenAPI / 事件 Schema / Flyway
→ 自动化测试和提交记录
```

只有总体设计文档而没有实现文档和验收证据时，不得标记为 `IMPLEMENTED`。

## 7. 强制维护规则

每次里程碑或已实现范围发生变化，负责该变更的 Agent 必须在同一提交或同一 PR 中同步更新本文件，至少包括：

1. `lastUpdated`；
2. `baselineCommit`，若提交 SHA 在提交前未知，可在 PR 合并后由后续维护提交补齐，变更说明中必须明确；
3. `latestMilestone`；
4. 能力实施总览中的状态、已完成范围、未完成范围和证据；
5. 最近里程碑章节；
6. 下一实施方向；
7. 与 `implementation-traceability-matrix.md`、里程碑实现文档和验收矩阵保持一致。

以下情况视为文档门禁失败：

- 新里程碑标记为 Implemented，但本文件未更新；
- 本文件声称完成，但没有对应代码、迁移、机器契约或测试证据；
- 删除或隐藏“未实现范围”；
- 使用模糊的“基本完成”“差不多完成”替代可验证范围；
- 最新基线和实际仓库进度明显不一致且没有说明。

## 8. 相关入口

- `serviceos-architecture/README.md`
- `serviceos-architecture/docs/implementation-traceability-matrix.md`
- `serviceos-architecture/roadmap/00-mvp-roadmap.md`
- `serviceos-architecture/roadmap/02-m7-application-delivery-plan.md`
- `serviceos-architecture/architecture/55-evidence-invalidate-runtime.md`
- `serviceos-architecture/testing/39-m42-evidence-invalidate-acceptance.md`
- `serviceos-architecture/architecture/54-evidence-task-completion-gate.md`
- `serviceos-architecture/testing/40-m43-dual-input-task-completion-acceptance.md`
