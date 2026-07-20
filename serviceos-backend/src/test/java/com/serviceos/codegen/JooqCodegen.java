package com.serviceos.codegen;

import com.serviceos.shared.infrastructure.jooq.JsonbStringConverter;
import com.serviceos.shared.infrastructure.jooq.TimestamptzInstantConverter;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.jooq.codegen.GenerationTool;
import org.jooq.codegen.JavaGenerator;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.ForcedType;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.jooq.meta.postgres.PostgresDatabase;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * ADR-091 P0：jOOQ 代码生成器。只运行在 test classpath（复用 testcontainers / postgresql /
 * flyway 依赖），由 {@code scripts/generate-jooq.sh} 通过 {@code mvn test-compile exec:java} 调用。
 *
 * <p>生成流程：启动一次性 PostgreSQL 容器 → 用与运行时完全一致的 Flyway 迁移建出 Schema →
 * 以该 Schema 为唯一基线运行 jOOQ GenerationTool → 生成物写入
 * {@code src/generated/java/com/serviceos/jooq/generated} 并提交进 git。生成前先清空旧生成物，
 * 保证被删除的表/列不会残留；输出对同一迁移基线是确定性的，重复生成不应产生 git diff
 * （由 {@code scripts/check-jooq-generated.sh} 门禁校验）。</p>
 *
 * <p>镜像与架构约定与 {@code scripts/verify-local.sh} 一致：默认 {@code postgres:18-alpine}，
 * 可用 {@code SERVICEOS_TEST_POSTGRES_IMAGE} 覆盖；不强制平台架构，由 verify-local 在
 * Apple Silicon/OrbStack 环境下清理 {@code DOCKER_DEFAULT_PLATFORM} 后复用本地原生镜像。</p>
 */
public final class JooqCodegen {
    /**
     * 必须与 {@code serviceos-backend/src/main/resources/application.yml} 的
     * {@code spring.flyway.locations} 保持同步（21 个模块迁移目录）。Flyway 迁移是
     * jOOQ 生成物的唯一 Schema 基线（ADR-091 §3.3），两处不一致会直接导致生成物漂移。
     */
    private static final List<String> FLYWAY_LOCATIONS = List.of(
            "classpath:db/migration/reliability",
            "classpath:db/migration/audit",
            "classpath:db/migration/authorization",
            "classpath:db/migration/project",
            "classpath:db/migration/task",
            "classpath:db/migration/operations",
            "classpath:db/migration/files",
            "classpath:db/migration/configuration",
            "classpath:db/migration/integration",
            "classpath:db/migration/workorder",
            "classpath:db/migration/workflow",
            "classpath:db/migration/dispatch",
            "classpath:db/migration/appointment",
            "classpath:db/migration/fieldwork",
            "classpath:db/migration/forms",
            "classpath:db/migration/evidence",
            "classpath:db/migration/sla",
            "classpath:db/migration/readmodel",
            "classpath:db/migration/identity",
            "classpath:db/migration/organization",
            "classpath:db/migration/network");

    private static final String OUTPUT_PACKAGE = "com.serviceos.jooq.generated";

    private JooqCodegen() {
    }

    public static void main(String[] args) throws Exception {
        Path moduleBaseDir = Path.of(System.getProperty("serviceos.basedir", "serviceos-backend"));
        Path outputDirectory = moduleBaseDir.resolve("src/generated/java");
        String image = System.getenv().getOrDefault("SERVICEOS_TEST_POSTGRES_IMAGE", "postgres:18-alpine");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(image))
                .withDatabaseName("serviceos")
                .withUsername("serviceos_codegen")
                .withPassword("serviceos_codegen")) {
            postgres.start();

            MigrateResult migration = migrate(postgres);
            System.out.println("[jooq-codegen] Flyway 已应用迁移 " + migration.migrationsExecuted + " 个。");

            // 先清空旧生成物再生成：jOOQ 只写不删，表/列被移除后残留文件会使一致性门禁失效。
            Path packageDirectory = outputDirectory.resolve(OUTPUT_PACKAGE.replace('.', '/'));
            deleteRecursively(packageDirectory);

            GenerationTool.generate(codegenConfiguration(postgres, outputDirectory));
            writePackageInfo(packageDirectory);

            long tableCount = countGeneratedTables(packageDirectory);
            System.out.println("[jooq-codegen] 生成完成：" + tableCount + " 张表 -> " + packageDirectory);
        }
    }

    /** 与运行时同源地执行 Flyway 迁移，保证生成基线 = 部署基线。 */
    private static MigrateResult migrate(PostgreSQLContainer<?> postgres) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(FLYWAY_LOCATIONS.toArray(String[]::new))
                // 与 application.yml 的 spring.flyway.validate-migration-naming 保持一致。
                .validateMigrationNaming(true)
                .load()
                .migrate();
    }

    private static Configuration codegenConfiguration(PostgreSQLContainer<?> postgres, Path outputDirectory) {
        return new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver(org.postgresql.Driver.class.getName())
                        .withUrl(postgres.getJdbcUrl())
                        .withUser(postgres.getUsername())
                        .withPassword(postgres.getPassword()))
                .withGenerator(new Generator()
                        .withName(JavaGenerator.class.getName())
                        .withDatabase(new Database()
                                .withName(PostgresDatabase.class.getName())
                                .withInputSchema("public")
                                .withExcludes("flyway_schema_history")
                                // 全局公共类型绑定（ADR-091 §3.3）：jsonb 保持 JSON 文本语义，
                                // timestamptz 统一 UTC + 微秒截断的 Instant 语义，UUID 保持原生映射。
                                .withForcedTypes(
                                        new ForcedType()
                                                .withUserType("java.lang.String")
                                                .withConverter(JsonbStringConverter.class.getName())
                                                .withIncludeTypes("(?i)jsonb"),
                                        new ForcedType()
                                                .withUserType("java.time.Instant")
                                                .withConverter(TimestamptzInstantConverter.class.getName())
                                                .withIncludeTypes("(?i)timestamptz")))
                        // 只生成 Table/Record 元类型；POJO/DAO 与领域模型职责重叠，不进入生成面。
                        .withGenerate(new Generate()
                                .withPojos(false)
                                .withDaos(false))
                        .withTarget(new Target()
                                .withPackageName(OUTPUT_PACKAGE)
                                .withEncoding(StandardCharsets.UTF_8.name())
                                .withDirectory(outputDirectory.toString())));
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** 生成物的包注释随生成一起重写，保证"禁止手改"说明始终与代码同在。 */
    private static void writePackageInfo(Path packageDirectory) throws IOException {
        Files.writeString(packageDirectory.resolve("package-info.java"), """
                /**
                 * 由 scripts/generate-jooq.sh 依据 Flyway 迁移基线生成的 jOOQ Schema 类型，禁止手改。
                 * 与迁移基线的一致性由 scripts/check-jooq-generated.sh 强制校验（ADR-091 §3.3）。
                 */
                package com.serviceos.jooq.generated;
                """, StandardCharsets.UTF_8);
    }

    private static long countGeneratedTables(Path packageDirectory) {
        Path tablesDirectory = packageDirectory.resolve("tables");
        if (!Files.isDirectory(tablesDirectory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(tablesDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).count();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
