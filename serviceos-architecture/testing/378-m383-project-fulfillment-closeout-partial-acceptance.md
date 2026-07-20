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
| M383-06 | OIDC UI 投影写链路 | 登录后 assign/claim/release | Playwright test 1 通过 |
| M383-07 | OIDC 全套件 19 项 | 全部绿 | **未闭合**（产品化定位债务） |
