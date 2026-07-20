---
title: M380–M382 履约编辑发布冻结验收矩阵
status: Accepted
milestone: M382
lastUpdated: 2026-07-20
---

# M380–M382 验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M380-01 | 编辑阶段并保存 | 草稿 aggregateVersion 递增 |
| M380-02 | 校验错误跳转 | 点击问题定位阶段 |
| M381-01 | 有阻断错误 | 发布下一步禁用 |
| M381-02 | 发布成功 | 生成不可变 Revision |
| M382-01 | 有 Profile 建单 | 冻结 PROFILE_REVISION |
| M382-02 | 无 Profile 建单 | LEGACY_BUNDLE + Bundle 冻结 |
| M382-03 | Profile 暂停 | 建单失败关闭 |
| M382-04 | 快照查询 | 返回冻结事实 / LEGACY 文案 |
