---
module: evidence
status: Partial
lastVerifiedMilestone: M53
---

# evidence 模块卡片

## 事实所有权

- EvidenceSlot、EvidenceItem、不可变 EvidenceRevision；
- 机器校验、槽位代次、lineage 和 EvidenceSetSnapshot；
- Task 提交资料门禁及显式 KEEP/INVALIDATE 处置。

Evidence 不拥有 StoredFile 物理生命周期、FormSubmission、ReviewCase 或 CorrectionCase。

## 公开边界

- 生产代码：`serviceos-backend/src/main/java/com/serviceos/evidence/`；
- 迁移：`serviceos-backend/src/main/resources/db/migration/evidence/`；
- 文件操作通过 files 模块公开 API，审核整改通过 review 模块公开 API/事件。

## 必读事实源

- `serviceos-architecture/architecture/10-evidence-review-correction.md`；
- M36～M53 中与本次切片直接相关的实现文档和验收矩阵；
- `serviceos-architecture/domain/06-state-machines.md`；
- Evidence 相关事件 Schema。

## 核心测试

```bash
rg --files serviceos-backend/src/test | rg '(Evidence|Correction|Review).*(Test|PostgresIT)'
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
```

不可变 Revision、Snapshot、条件重解析、代次处置和文件联动必须运行对应 PostgreSQL IT。

## 相邻模块

- 上游：configuration、forms、files；
- 下游：review、task；
- 只有文件生命周期变化时展开 files；
- 只有审核决定或整改变化时展开 review。

## 稳定不变量

- EvidenceRevision 和 Snapshot 只追加，不覆盖历史；
- Task 完成只接受同 Task、同项目、同冻结版本的有效引用；
- 条件重解析不能恢复旧槽位或改写历史 Snapshot；
- 作废必须显式授权、审计并联动文件状态；
- 当前未证明 OCR/CV/GPS 权威结果。

## 扩大检索触发条件

配置条件、表单事实、文件安全、审核整改、Task 完成门禁、事件或数据库 lineage 变化。
