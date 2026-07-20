---
title: M385 项目履约配置中心产品化（切片 A）
status: Implemented
milestone: M385
lastUpdated: 2026-07-20
relatedMilestones: [M378, M383, M384]
openapiVersion: "1.0.62"
flywayVersion: "138"
---

# M385 项目履约配置中心产品化（切片 A）

## 已实现

1. Admin Design Token / Ant Theme 对齐经典专业风企业蓝 `#1677FF`；
2. AppShell 消费服务端 `MeNavigationItem.section`；Admin Page Registry section 映射产品 IA（catalog `page-registry-v18`）；
3. 共享组件：`PageHeader`、`SummaryStrip`、`ConfigurationSubNav`、`RightContextRail`、`ConfigurationWorkspaceLayout`；
4. 项目履约配置中心母版（概览 / 工单类型表 / 发布记录 / 右轨影响）；
5. 新增工单类型向导（标准 / 复制 / 空白）；
6. OpenAPI 1.0.62：`compile-preview` 返回产品化 `runbook`；新增 `GET .../compare-impact`；
7. 发布/预览/详情消费 Runbook 与真实差异；Manifest JSON 仅进诊断抽屉；
8. 主操作按 Profile `allowedActions` 显隐；
9. 单元测试、ArchitectureTest、PostgresIT、Playwright 功能/视觉证据。

## 明确未实现

- 完整结构化 Draft DTO 替换 `documentJson` 编辑器（后续 M385b）；
- 工作流设计器 / 任务模板中心完整页（M386/M387）；
- 使用中工单数跨模块读模型（页面诚实标注 UI_DATA_GAP）；
- 左二级导航中工作流/表单/SLA 等分区的产品编辑器；
- 产品负责人人工视觉批准（`READY_FOR_REVIEW`，非 `PRODUCT_ACCEPTED` / `VISUAL_APPROVED`）；
- OIDC Playwright test 7/8。

## 证据入口

- Backend：`ProjectFulfillmentRunbookAssemblerTest`、`ProjectFulfillmentCompareAnalyzerTest`、`ProjectFulfillmentProfilePostgresIT`、`ArchitectureTest`
- Admin：`tests/unit/fulfillment-product-guard.test.mjs`、`tests/e2e/admin-fulfillment-profiles.spec.ts`、`tests/e2e/admin-fulfillment-visual.spec.ts`
- 截图：`serviceos-admin-web/tests/e2e/__screenshots__/fulfillment-*.png`
