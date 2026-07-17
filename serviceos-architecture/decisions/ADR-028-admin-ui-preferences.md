---
title: ADR-028：Admin UI Preference 归属 readmodel 与最小授权
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Admin Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-027-admin-personal-saved-view.md
---

# ADR-028：Admin UI Preference 归属 readmodel 与最小授权

## 1. 状态与已接受决策

本 ADR 作为 M190 的边界与授权结论，正式接受：

1. Admin **个人** UI Preference 由现有模块 `readmodel` 拥有（表 `rdm_ui_preference`）；**不**新建
   preference 模块，也**不**把偏好写入 `authorization`；
2. HTTP 契约仅接受：`GET /api/v1/me/ui-preferences?portal=ADMIN`、
   `PUT /api/v1/me/ui-preferences`、`DELETE /api/v1/me/ui-preferences/{key}`；
3. **授权选择（最小，镜像 ADR-027）**：任意已认证主体可读写**自己的**偏好（principal + tenant
   作用域）；不新增 `preference.manageUi` Capability。偏好不授予页面或数据能力；
4. Portal 必须为 `ADMIN`；其他 Portal 失败关闭（`VALIDATION_FAILED`）；
5. 偏好键白名单：`theme`、`density`、`locale`、`reduceMotion`、`defaultSavedViews`、
   `columnWidths`；未知或禁止键 `UI_PREFERENCE_KEY_NOT_ALLOWED`（422）；
6. 禁止任何关闭安全确认、隐藏必填、绕过脱敏、禁用事务/安全通知的键；
7. 并发按 preference_key 乐观版本（`aggregateVersion`）；冲突 `VERSION_CONFLICT`；
8. 明确不做：共享偏好、Network/Technician Portal、设计系统级主题引擎。

## 2. 上下文

API-06 §9 / DATA-06 §7 长期为草案。M189 交付个人 SavedView 后，Admin 主题/密度/减少动画与默认
SavedView 绑定是改善运营体验的最小可靠切片，且不改变领域授权真相。偏好与 SavedView 同属用户
拥有的真实数据，继续放在 `readmodel`。

## 3. 后果

- ArchitectureTest 继续验证 `readmodel` 依赖方向；无需新增 Capability 种子；
- RoleGrant 撤销后偏好行可保留，但页面导航与业务查询仍 403；
- 其他 Portal 或共享偏好若未来需要，必须另接受 API-06/DATA-06 切片与独立证据，不得静默扩展。
