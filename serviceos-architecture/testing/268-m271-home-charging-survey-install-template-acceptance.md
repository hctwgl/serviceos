---
title: M271 标准家充勘测安装配置模板验收矩阵
status: Implemented
milestone: M271
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M271-01 | 模板无车企 DTO | JSON 不含 BYD CPIM 字段 | Template IT |
| M271-02 | 漂移门禁 | 架构源 = classpath | SchemaDriftTest |
| M271-03 | 可发布 Bundle | WORKFLOW+SLA 发布成功 | Template IT |
| M271-04 | 冒烟推进 | Gateway+WAIT 全链路 FULFILLED | Template IT |
