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

1. 无 Profile 正式建单失败关闭；A/B 冻结 IT；
2. `blockedActions` + 表单/资料 `explainBlockingReasons`；
3. 工单详情「配置来源」+ `/fulfillment-snapshot`；
4. Admin 履约 API 经 `@serviceos/core-client`；列表/详情/编辑/预览/发布/快照与 a11y smoke；
5. Pilot seed + `project.fulfillment.*`；OIDC 入站 CREATE `ACCEPTED`；
6. OIDC Playwright：**tests 1–5** 绿（投影写链路、完工、整改豁免、强制通过、补传复审完结）；final-review visual/workspace 绿；
7. 工作区补齐「集成」「预约到场」产品页签；Task 详情 prepared-complete 刷新 allowed-actions。

## 明确未闭合

| 项 | 状态 |
|---|---|
| OIDC tests 6–8（预约上门 / 外发 CLIENT / 入站长链路） | 推进中（侧链文案与产品化 IA 对齐） |
| Playwright UI 内发布→建单 A/B→快照全链路 | 未做（后端 A/B IT 已覆盖冻结） |
| Admin 全站 core-client | 仅履约模块 |

**不得宣称「项目工单履约配置完整实施完成」**；M378～M382 主链路与 M383 关键收口证据已具备。
