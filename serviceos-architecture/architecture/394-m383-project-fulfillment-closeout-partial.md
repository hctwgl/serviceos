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

1. 无 Profile 正式建单失败关闭；A/B 冻结 PostgreSQL IT；
2. `blockedActions` + 表单/资料 `explainBlockingReasons`；
3. 工单详情「配置来源」+ `/fulfillment-snapshot`；
4. Admin 履约 API 经 `@serviceos/core-client`；列表/详情/编辑/预览/发布/快照与 a11y smoke；
5. Pilot seed + `project.fulfillment.*`；OIDC 入站 CREATE `ACCEPTED`；
6. OIDC Playwright **tests 1–6** 绿：投影写链路、完工推进、整改豁免、强制通过、补传复审完结、预约提议/签到签退；
7. final-review visual + workspace mock **绿**；
8. 工作区补齐「集成」「预约到场」产品页签；Task 详情 prepared-complete 刷新 allowed-actions。

## 明确未闭合（本里程碑停止推进）

| 项 | 状态 | 说明 |
|---|---|---|
| OIDC test 7（审核外发 + 厂端回调关闭 CLIENT Case） | **未闭合** | 产品化后核心时间线/回执侧链定位仍不稳定 |
| OIDC test 8（入站长链路：领取→预约→整改→外发） | **未闭合** | 依赖集成/预约侧链与长链路编排，非履约 Profile 本体 |
| `verify-admin-smoke.sh` 全套 19 项 | **部分**（最佳 **17 passed / 2 failed**） | 不得记「全绿」 |
| Playwright UI 内发布→建单 A/B→快照 | **未做** | 后端 A/B 冻结 IT 已覆盖版本隔离 |
| Admin 全站 `@serviceos/core-client` | **未做** | 仅履约模块已切换 |

**不得宣称「项目工单履约配置完整实施完成」。**  
M378～M382 主链路与本里程碑声明范围内的收口证据已具备；剩余项属 Admin Pilot 产品化/长链路债务，不阻塞履约 Profile 能力声明。
