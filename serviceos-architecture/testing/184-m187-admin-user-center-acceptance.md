---
title: M187 Admin 统一用户中心验收矩阵
status: Implemented
milestone: M187
lastUpdated: 2026-07-17
---

# M187 Admin 统一用户中心验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M187-01 | 按姓名/工号搜索并选择人员 | 使用 `GET /security-principals?query=`；不要求复制 principal UUID | pass（E2E） |
| M187-02 | 查看主体详情 | 分区显示身份、Persona、绑定/授权来源与待重分配，不混成单一角色字段 | pass（E2E） |
| M187-03 | 调动、停用或撤权相关写流 | 展示影响/obligations；Idempotency-Key + If-Match；成功后重读权威状态 | pass（E2E 组织任职/影响面板；撤权 UI 具备） |
| M187-04 | 低权限管理员打开治理深链 | 安全无权/不存在文案；不泄露姓名、工号、角色或联系方式；导航隐藏入口 | pass（E2E viewer） |
| M187-05 | 并发修改 | 过期 If-Match 返回 409，刷新后可恢复，不覆盖新事实 | pass（E2E） |
| M187-06 | EXTERNAL_AUTHORITATIVE 组织 | 来源字段只读徽章与同步指示可见 | pass（E2E） |
| M187-NAV-01 | Capability 门禁导航 | 无对应目录读能力时隐藏用户中心入口 | pass（E2E + 探测） |
| M187-SEED-01 | 本地 developer 能力 | seed 含 organization/network/authorization 治理能力 | pass（SQL） |

## 证据

- `serviceos-admin-web/tests/e2e/admin-user-center.spec.ts`
- `bash serviceos-deploy/admin-pilot/verify-admin-smoke.sh`（或同等 Playwright 环境）
- `cd serviceos-admin-web && npm ci && npm run build`
- `bash scripts/agent-verify.sh docs`
- `bash scripts/verify-local.sh`（后端无 Flyway 变更，确认基线仍绿）
