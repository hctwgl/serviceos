---
title: M378 项目履约配置领域基础（Profile/Revision/OpenAPI/Flyway）
status: Implemented
milestone: M378
lastUpdated: 2026-07-20
relatedMilestones: [M375, M282, M283, M16]
openapiVersion: "1.0.60"
flywayVersion: "136-137"
---

# M378 项目履约配置领域基础

## 已实现

1. ADR-091 Accepted；
2. Flyway **V136**：`cfg_project_fulfillment_profile` / `_revision`、生效窗口 exclusion、已发布不可变触发器、capability 种子；
3. Flyway **V137**：工单冻结列 + `LEGACY_BUNDLE` 默认；
4. OpenAPI **1.0.60**：履约 Profile/草稿/校验/预览/发布/版本/暂停恢复 + 工单 snapshot 契约；
5. `ProjectFulfillmentProfileService` 创建/列表/详情/草稿读写/校验/预览/发布/暂停恢复；
6. `ProjectFulfillmentResolver` 骨架（建单接入在 M382）；
7. Manifest 编译器与结构化校验器；
8. ProblemCode：`PROJECT_FULFILLMENT_*`。

## 明确未实现

- Admin 产品化工作区与发布四步流（M379～M381）；
- 全部正式建单入口接入 Resolver 与 WO 写冻结（M382）；
- 阶段细粒度编辑 API（reorder/upsert stage 专用命令在 M380）；
- 工单 snapshot HTTP 实现（契约已存在，实现随 M382）；
- 完整 30 条校验规则与动作目录产品化。

## 验证

```bash
bash scripts/agent-verify.sh compile
bash scripts/agent-verify.sh test ProjectFulfillmentManifestCompilerTest
bash scripts/agent-verify.sh it ProjectFulfillmentProfilePostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh docs
```
