---
title: M282 Workflow 配置设计器 API 验收矩阵
status: Implemented
milestone: M282
---

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M282-01 | 创建/更新 WORKFLOW 草稿 | DRAFT 持久化且版本递增 | PASS |
| M282-02 | 校验合法定义 | VALIDATED | PASS |
| M282-03 | 校验非法网关 | 失败关闭且保留 validationErrors | PASS |
| M282-04 | 发布已校验草稿 | PUBLISHED + 不可变 version | PASS |
| M282-05 | 非 WORKFLOW 类型 | VALIDATION_FAILED | PASS |
| M282-06 | OpenAPI/架构 | 契约可解析；Modulith 通过 | PASS |
