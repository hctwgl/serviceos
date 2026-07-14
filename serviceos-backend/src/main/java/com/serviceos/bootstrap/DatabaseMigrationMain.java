package com.serviceos.bootstrap;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * 与服务端共用同一可执行镜像的数据库迁移入口。
 *
 * <p>迁移任务只装配 Flyway/JDBC，不启动 Web、Worker、OIDC 或业务 Bean。生产应用账号必须关闭
 * Spring Flyway 自动迁移并且没有 DDL 权限，迁移凭据只注入一次性 deployment job。</p>
 */
public final class DatabaseMigrationMain {
    private static final String[] LOCATIONS = {
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
            // 部署迁移入口必须与 Spring Flyway locations 同步，否则运行时会引用尚未建表的调度能力。
            "classpath:db/migration/dispatch"
    };

    private DatabaseMigrationMain() {
    }

    public static void main(String[] args) {
        String url = requiredEnvironment("SERVICEOS_DB_URL");
        String username = requiredEnvironment("SERVICEOS_DB_USERNAME");
        String password = requiredEnvironment("SERVICEOS_DB_PASSWORD");

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations(LOCATIONS)
                .validateMigrationNaming(true)
                .load();
        MigrateResult result = flyway.migrate();
        MigrationInfo current = flyway.info().current();
        String currentVersion = current == null || current.getVersion() == null
                ? "none"
                : current.getVersion().getVersion();

        // 只输出版本和数量，不输出 JDBC URL、账号或异常正文中的潜在凭据。
        System.out.printf(
                "{\"event\":\"database_migration_completed\",\"version\":\"%s\",\"migrationsExecuted\":%d}%n",
                currentVersion,
                result.migrationsExecuted);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required deployment environment variable is missing: " + name);
        }
        return value;
    }
}
