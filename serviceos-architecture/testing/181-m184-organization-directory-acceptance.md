---
title: M184 企业组织与任职目录验收矩阵
status: Draft
milestone: M184
lastUpdated: 2026-07-17
---

# M184 企业组织与任职目录验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M184-01 | 建立多层 OrgUnit | closure 祖先/后代与 depth 正确；循环、自环、跨租户父节点被拒绝 |
| M184-02 | 员工调部门、兼职和负责人变化 | 旧 Membership 保留；新有效期准确；同一时刻仅一条有效 PRIMARY |
| M184-03 | 离职/停用同步 | Principal 新操作失权；有效 RoleGrant 被撤销；产生 OPEN 待重分配清单 |
| M184-04 | EXTERNAL_AUTHORITATIVE 字段被普通 Admin 修改 | 失败关闭；持有 override 能力时可显式覆盖并审计 |
| M184-05 | 同一外部同步批次重放、乱序或部分失败 | 批次幂等；乱序 SKIPPED；部分失败不回退已成功更高版本 |
| M184-06 | 组织移动 | closure 原子切换；历史审计可解释；跨租户/成环失败关闭 |
| M184-SEC-01 | 无 `organization.read` 查询组织 | 403，不泄露组织名称或成员 |
| M184-SEC-02 | 跨租户读取组织/任职 | 404 失败关闭 |
| M184-IDEM-01 | 相同 Idempotency-Key 重放创建单元 | 返回同一结果，不产生第二单元 |
| M184-ARCH-01 | organization 读取 authorization/identity 内部表 | ArchitectureTest 阻断 |
