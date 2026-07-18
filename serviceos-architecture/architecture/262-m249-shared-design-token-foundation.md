---
title: M249 多端共享 Design Token 基础
status: Implemented
milestone: M249
lastUpdated: 2026-07-18
relatedMilestones: [M247, M248]
---

# M249 多端共享 Design Token 基础

## 范围与证据

- 新增版本化 `serviceos-design-tokens-v1.json` 作为 Web/iOS 视觉基础单一机器源；
- 当前覆盖基础颜色、间距、圆角、字体/行高和卡片阴影，取值来自既有 Admin 基线，不定义页面、组件、
  Portal、角色或数据权限；
- Node 生成器确定性产出 Web CSS variables 与平台无关的 Swift 6 常量，并记录源文件和两个输出 SHA；
- 门禁连续生成两次并比较树摘要，精确验证关键 CSS 变量，并以 Swift 6 严格模式编译 Swift 输出；
- Maven `DesignTokenContractTest` 验证版本、必需分类、关键值和无角色词边界；contracts 测试 38 项通过；
- Core OpenAPI 仍为 `1.0.20`，Flyway 仍为 100/102，无 HTTP、事件或数据库变化。

## 明确未实现

独立 Web/iOS 工程消费、Admin 全量样式迁移、暗色 Token、Dynamic Type、VoiceOver、图标与文案包、
视觉回归和设计制品发布。

## 失败关闭语义

未知顶层分类、非法 token 名、空值、非正数维度、非大写六位 HEX、角色词、生成漂移、CSS 探针失败或
Swift 6 编译失败均阻断。生成物只进入 `target/`，不得手工维护双份平台常量。
