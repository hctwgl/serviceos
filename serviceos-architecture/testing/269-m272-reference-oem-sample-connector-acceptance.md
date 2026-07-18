---
title: M272 REFERENCE_OEM SAMPLE Connector 验收矩阵
status: Implemented
milestone: M272
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M272-01 | SAMPLE 验签通过 | 经通用管道创建工单 | ReferenceOem IT |
| M272-02 | transport 重放 | replay=true，不重复建单 | ReferenceOem IT |
| M272-03 | 文档标记 | REFERENCE/SAMPLE/TBD_EXTERNAL_CONTRACT | 实现文档 |
| M272-04 | 核心防污染 | 核心不依赖 referenceoem | ArchitectureTest |
