---
title: ADR-057：Technician Portal TECHNICIAN.ME /me 页壳
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Technician Portal Owner
related_adrs:
  - decisions/ADR-033-technician-portal-feed.md
  - decisions/ADR-056-technician-portal-feed-field-enrichment.md
---

# ADR-057：Technician Portal TECHNICIAN.ME /me 页壳

## 1. 状态与已接受决策

本 ADR 作为 M219 的边界结论，正式接受：

1. 交付 Page Registry 已登记的 `TECHNICIAN.ME` 独立路由
   `/technician-portal/me`（此前错误别名到 sync-summary）；
2. 仅消费 M188 Accepted API：
   - `GET /api/v1/me`
   - `GET /api/v1/me/contexts`
   - `GET /api/v1/me/capabilities?contextId=`（可选 `expectedContextVersion`）
3. 展示既有非 PII 字段：principal/tenant/displayName/personas、当前 TECHNICIAN
   context（contextId/portal/scope*/version）、capabilityCodes、asOf/contextVersion；
4. **不**新增 HTTP、**不**升 OpenAPI、**不**新增 Flyway、**不**新增 pageId
   （catalog 仍 `page-registry-v16`；OpenAPI 仍 `1.0.0`）；
5. **不**接受：`TECHNICIAN.PROFILE` 资质/设备/设置、`TASK.DETAIL`、`MESSAGE`、
   离线工作包、客户 PII、Network Portal SLA/Visit/表单 DTO 发明。

## 2. 上下文

`CodePageRegistry` 与 M188 已登记 `TECHNICIAN.ME`，但 Admin Web 将路由别名到
sync-summary，且未调用 `/me/capabilities`。零契约补齐「我的」页壳即可闭合该缺口。

## 3. 后果

- Admin Web Technician Portal Me 页 + 导航修正 + E2E；
- 完整师傅个人中心 / 消息 / 离线仍属后续接受范围。
