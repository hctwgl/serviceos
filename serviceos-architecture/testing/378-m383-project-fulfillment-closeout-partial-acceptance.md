---
title: M383 项目履约收口（部分）验收矩阵
status: Accepted
milestone: M383
lastUpdated: 2026-07-20
---

# M383 验收矩阵（部分）

| ID | 场景 | 期望 | 结果 |
|---|---|---|---|
| M383-01 | 项目详情空壳移除 | 无配置设计器占位文案 | Playwright mock 通过 |
| M383-02 | 履约列表可达 | 显示方案名称 | Playwright mock 通过 |
| M383-03 | A/B 冻结隔离 | 发布 v2 后旧工单仍 v1 | PostgreSQL IT 通过 |
| M383-04 | 动作阻塞解释 | 缺表单/资料返回 blockedActions | 单元测试通过 |
| M383-05 | OIDC 入站建单 | Profile seed 后 ACCEPTED | `verify-admin-smoke` 后端步骤通过 |
| M383-06 | OIDC UI 投影写链路 | assign/claim/release | Playwright test 1 通过 |
| M383-07 | OIDC 完工推进 | 表单/资料/complete | Playwright test 2 通过 |
| M383-08 | OIDC 整改豁免 / 强制通过 | 驳回→豁免；强制通过重开 | Playwright tests 3–4 通过 |
| M383-09 | OIDC 补传复审完结 | 驳回→补传→复审→complete | Playwright test 5 通过 |
| M383-10 | OIDC 预约上门 | 提议/确认/签到签退 | Playwright test 6 通过 |
| M383-11 | OIDC 外发 CLIENT 回调 | 外发 ACK + 厂端回调关闭 | **未闭合**（test 7） |
| M383-12 | OIDC 入站长链路 | 领取→预约→整改→外发 | **未闭合**（test 8） |
| M383-13 | `verify-admin-smoke` 全套 | 19 项全绿 | **部分**：最佳 17/19；停止推进 |
