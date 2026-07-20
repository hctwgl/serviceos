---
title: M383 项目履约产品化收口（部分）
status: Implemented
milestone: M383
lastUpdated: 2026-07-20
relatedMilestones: [M378, M379, M380, M381, M382]
openapiVersion: "1.0.61"
flywayVersion: "138"
---

# M383 项目履约产品化收口（部分）

## 已实现

1. 无 Profile 正式建单失败关闭（项目已存在 Profile 时不得回退 LEGACY Bundle）；
2. A/B 冻结 IT：发布 v2 后旧工单仍冻 v1、新工单冻 v2；
3. `blockedActions` + 表单/资料 `explainBlockingReasons`（缺校验提交、缺必传槽位中文名）；
4. 工单详情「配置来源」+ `/fulfillment-snapshot`；
5. Admin 履约 API 经 `@serviceos/core-client`；列表/详情/编辑/预览/发布/快照页与 a11y smoke；
6. Pilot seed 含已发布履约 Profile；local-project-admin 授予 `project.fulfillment.*`；
7. 真实 OIDC：入站 CREATE `ACCEPTED`；Playwright 套件 **test 1**（目录投影 + assign/claim/release）通过；
8. Task 详情：表单/资料 prepared-complete 后刷新 allowed-actions，确保 complete/`resultRef` 面板出现。

## 明确未实现 / 未闭合

| 项 | 状态 |
|---|---|
| Playwright 全链路（UI 内发布→建单 A/B→快照） | 未完成（后端 A/B IT 已覆盖冻结隔离） |
| a11y/视觉全量基线 | 部分（履约列表 a11y smoke；非全站） |
| 完整 `verify-admin-smoke.sh`（19 项） | **部分**：test 1 绿；后续用例受 Admin 产品化定位债务影响，未宣称全绿 |
| Admin 全站迁移 core-client | 仅履约模块完成 |

因此：**不得宣称「项目工单履约配置完整实施完成」**——M378～M382 主链路与 M383 关键收口证据已具备，完整 OIDC UI 套件与全站 a11y/视觉仍属后续产品化债。
