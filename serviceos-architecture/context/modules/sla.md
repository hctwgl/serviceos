---
module: sla
status: Partial
lastVerifiedMilestone: M61
---

# sla 模块卡片

## 事实所有权

- SLA 策略快照、SlaInstance、时钟区段和里程碑；
- RUNNING、BREACHED、MET、MET_LATE 等 SLA 事实；
- SLA 目标时间对账和只追加的时钟历史。

SLA 不拥有 Task 生命周期、通知发送或运营异常状态。

## 公开边界

- 生产代码：`serviceos-backend/src/main/java/com/serviceos/sla/`；
- 迁移：`serviceos-backend/src/main/resources/db/migration/sla/`；
- Task 与 SLA 通过公开 API/事件协作，禁止互相写内部表。

## 必读事实源

- `serviceos-architecture/architecture/12-sla-clock-escalation.md`；
- M61 SLA 实现文档和验收矩阵；
- `serviceos-architecture/domain/06-state-machines.md`；
- SLA 事件 Schema。

## 核心测试

```bash
rg --files serviceos-backend/src/test | rg 'Sla.*(Test|PostgresIT)'
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
```

时钟推进、重复事件、边界时间、并发和迁移必须使用真实 PostgreSQL IT。

## 相邻模块

- 上游：task、configuration；
- 下游：notification、operations；
- 暂停原因来自 WorkOrder/Task 时才展开对应状态机；
- 预警和升级实现时展开 notification、authorization 和 operations。

## 稳定不变量

- 策略版本和摘要在实例创建时锁定；
- 同一阈值只能产生一次业务事实；
- 重复、迟到和乱序事件不得回退时钟；
- SLA 不直接修改 Task 或 WorkOrder 状态；
- 当前只证明 ELAPSED 时钟，不能外推为业务日历。

## 扩大检索触发条件

业务日历、暂停恢复、免责重算、预警升级、通知、其他 subject、结算考核或公开 API 变化。
