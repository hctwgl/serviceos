# jOOQ 生成物（ADR-091 P0）

`java/com/serviceos/jooq/generated` 由 `bash scripts/generate-jooq.sh` 以
`src/main/resources/db/migration` 的 Flyway 迁移基线为唯一 Schema 事实源生成，
**禁止手改**（ADR-091 §3.3）。

- 重新生成：`bash scripts/generate-jooq.sh`（幂等可重复）；
- 一致性门禁：`bash scripts/check-jooq-generated.sh`（供 CI 调用），生成物与迁移基线
  不一致即失败；
- 公共类型绑定（jsonb→String、timestamptz→Instant、UUID 原生）见
  `src/main/java/com/serviceos/shared/infrastructure/jooq/` 与生成器
  `src/test/java/com/serviceos/codegen/JooqCodegen.java` 中的 forcedTypes；
- 生成器只生成 Table/Record 元类型，不生成 POJO/DAO（与领域模型职责不重叠）。
