---
title: M378 项目履约配置领域基础验收矩阵
status: Accepted
milestone: M378
lastUpdated: 2026-07-20
---

# M378 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M378-01 | 同项目同服务产品创建两次 | 第二次失败关闭 | PostgresIT |
| M378-02 | 创建 Profile 生成草稿 Revision | status=DRAFT，draft_revision_id 非空 | PostgresIT |
| M378-03 | 更新草稿 If-Match 冲突 | VERSION_CONFLICT | PostgresIT |
| M378-04 | 无 Workflow/Bundle 发布 | VALIDATION_FAILED | PostgresIT |
| M378-05 | 绑定后发布 | 不可变 PUBLISHED + Manifest digest | PostgresIT |
| M378-06 | 已发布 Revision UPDATE | DB 触发器拒绝 | PostgresIT |
| M378-07 | Manifest 编译确定性 | 两次 digest 相同 | Unit |
| M378-08 | OpenAPI 兼容 | 相对 master 无破坏 | contracts |
| M378-09 | ArchitectureTest | 模块边界通过 | arch |
