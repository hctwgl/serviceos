---
title: M50 整改 Task 自动候选人
version: 0.1.0
status: Implemented
---

# M50 整改 Task 自动候选人

## 1. 实现范围

1. CorrectionCase 打开创建 `evidence.correction` Task 时，读取源 Evidence Task 的 ACTIVE `RESPONSIBLE` USER；
2. 若存在，则同事务为整改 Task 写入 ACTIVE `CANDIDATE`（`sourceType=SYSTEM`，`sourceId=CORRECTION_AUTO_CANDIDATE`）；
3. 不自动 claim/start，不改师傅原 Task 状态；无责任人时保持仅 READY、无候选人；
4. OpenAPI 不变（无新外部命令）；Flyway **V050** 允许 assignment batch `source_type=SYSTEM`；staging **050/52**。

## 2. 未实现

策略评分多候选人、网点容量、自动 claim、条件槽位、`WAIVED`。
