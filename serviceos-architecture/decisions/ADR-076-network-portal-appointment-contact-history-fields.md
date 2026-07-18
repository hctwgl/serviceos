---
title: ADR-076：Network Portal 预约/联系历史 Accepted 字段展示
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-054-network-portal-workspace-technician-fanin.md
  - decisions/ADR-058-network-portal-queue-field-enrichment.md
  - decisions/ADR-075-network-portal-workbench-stats-time.md
---

# ADR-076：Network Portal 预约/联系历史 Accepted 字段展示

## 1. 状态与已接受决策

本 ADR 作为 M238 的边界结论，正式接受：

1. 在 `NETWORK.TASK.QUEUE` 任务页的预约/联系历史列表上做 **UI-only** enrichment：展示
   OpenAPI 已要求、客户端可声明但历史行未渲染的非 PII 字段；
2. 预约历史至少展示：`createdBy`（操作者）、当前 revision 的 `confirmationChannel`（渠道）、
   `confirmedPartyType`、当前 revision `window`（起止/时区）、既有 `type/status/version`；
3. 联系历史至少展示：`actorId`（操作者）、既有 `channel`/`resultCode`；
4. **禁止**渲染 `addressRef`、`note`、`contactedPartyRef`、`recordingRef` 等 PII/地址字段
   （延续 ADR-054）；
5. **不**扩展工作区/目录薄摘要契约（Admin 对齐摘要仍无 actor/createdBy）；
6. **不**新增 HTTP/字段、**不**升 OpenAPI（仍 `1.0.16`）、**不**新增 Flyway、**不**新增 pageId；
7. **不**接受：今日/明日预约计数、notifications、Portal ACK、客户 PII、独立预约中心页重设计。

## 2. 上下文

product/03 §8 要求网点预约/联系历史「必须显示操作者和渠道，不把网点代约伪装成师傅预约」。
M197/M199 已返回完整 `Appointment`/`ContactAttempt`，但任务页历史仅渲染 id/status/version
或 id/result/channel，未闭合该规则。沿用 M218/M220/M237 UI-only 模式即可零契约推进。

## 3. 后果

- Admin Web 任务页预约/联系历史 enrichment + E2E；
- 工作区/目录旁载摘要若需操作者，须另接受 OpenAPI 扩展。
