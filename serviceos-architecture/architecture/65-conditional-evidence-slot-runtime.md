---
title: M52 条件 EvidenceSlot requiredWhen 运行时
version: 0.1.0
status: Implemented
---

# M52 条件 EvidenceSlot requiredWhen 运行时

## 1. 实现范围

1. ADR-018 布尔 `SERVICEOS_EXPR_V1` 子集在 `configuration::api` 落地；
2. `task.created@v1` 消费时加载 WorkOrder/region/task 上下文，求值 Evidence `requiredWhen`；
3. 条件 **true** → 创建槽位，`required_flag=true`，`minCount=max(配置,1)`；
4. 条件 **false** → 不创建槽位；
5. 非法语法/未知路径/类型错误 → 整笔解析失败回滚；
6. EVIDENCE 资产发布期复用同一解释器执行语法、类型、白名单与复杂度静态门禁；
7. `resolverVersion=CONDITIONAL_EVIDENCE_V1`；解析级事实固化完整条件上下文摘要和 true/false
   决策解释，槽位级事实继续保存命中解释；
8. 表达式长度、递归深度和操作符数量均有硬上限，超限失败关闭；
9. 无 OpenAPI 变更；Flyway V052 为不可变解析事实增加 `condition_input_digest` 与
   `resolution_explanation`，staging 期望 052 / 54。

## 2. 模块与事务边界

- Evidence 只依赖 `configuration::api` 的无副作用求值端口和 `workorder::api` 的只读上下文端口；
- WorkOrder JDBC 查询位于 infrastructure，所有查询同时携带 tenantId 与 workOrderId；
- Inbox、解析级事实、槽位、审计和 Outbox 在同一事务内提交；非法表达式、缺失上下文和非法
  `minCount` 均回滚整笔消费；
- 条件为 true 时不会把显式 `minCount=0` 静默修正为 1，而是拒绝不一致配置。

## 3. 未实现

表单字段驱动中途重解析（M53）、决策表/公式/脚本层、OCR/CV。
