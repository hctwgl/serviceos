# ServiceOS 工程规范索引

状态：Accepted  
适用范围：后端、OpenAPI、数据库、测试、运维日志、原生 iOS 和 Web 工程。

## 强制规范

1. [中文友好开发与代码注释规范](./01-chinese-friendly-development.md)
2. [日志、异常与错误模型规范](./02-logging-and-error-standard.md)
3. [数据库设计、命名与描述规范](./03-database-design-and-description-standard.md)
4. [破坏性变更与禁止兜底规范](./04-no-fallback-and-breaking-change-standard.md)
5. [自动化质量门禁](./05-quality-gates.md)

## 总原则

- 面向人的内容使用准确、自然的简体中文；
- 面向程序的稳定标识使用规范英文；
- 代码注释解释业务原因、约束和失败恢复，不逐行翻译代码；
- 日志事件说明使用中文，结构化检索字段保持英文；
- 错误代码保持稳定英文，标题、详情和下一步操作使用中文；
- 数据库对象使用英文命名，并通过中文 `COMMENT` 完整描述；
- 不兼容旧开发代码、旧数据、旧接口和旧文档；
- 不允许静默兜底、双轨、默认成功或以内部 ID 替代业务名称。

这些规范属于合并门禁。新代码不符合规范时不得以“后续补充”为理由合并。
