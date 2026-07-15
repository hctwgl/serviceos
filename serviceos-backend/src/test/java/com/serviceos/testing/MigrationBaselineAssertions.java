package com.serviceos.testing;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 从测试 classpath 中的真实 Flyway 资源推导当前迁移终点，避免每增加一个迁移就同步修改多处数字断言。
 */
public final class MigrationBaselineAssertions {
    private static final String MIGRATION_PATTERN = "classpath*:db/migration/**/*.sql";
    private static final Pattern VERSIONED = Pattern.compile("^V([0-9]+)__.+\\.sql$");
    private static final Pattern REPEATABLE = Pattern.compile("^R__.+\\.sql$");

    private MigrationBaselineAssertions() {
    }

    public static void assertCurrentAndRepeatable(Flyway flyway) {
        MigrationBaseline baseline = discover();

        assertThat(flyway.info().current())
                .as("Flyway current migration")
                .isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion())
                .isEqualTo(baseline.latestVersion());
        assertThat(flyway.info().applied())
                .hasSize(baseline.migrationCount());
        assertThat(flyway.migrate().migrationsExecuted)
                .isZero();
    }

    private static MigrationBaseline discover() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(MIGRATION_PATTERN);
            List<String> migrationNames = Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .filter(Objects::nonNull)
                    .filter(MigrationBaselineAssertions::isMigration)
                    .distinct()
                    .sorted()
                    .toList();
            String latestVersion = migrationNames.stream()
                    .map(VERSIONED::matcher)
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .max(Comparator.comparingInt(Integer::parseInt))
                    .orElseThrow(() -> new IllegalStateException("No versioned Flyway migrations found"));
            return new MigrationBaseline(latestVersion, migrationNames.size());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot discover Flyway migration resources", exception);
        }
    }

    private static boolean isMigration(String filename) {
        return VERSIONED.matcher(filename).matches() || REPEATABLE.matcher(filename).matches();
    }

    private record MigrationBaseline(String latestVersion, int migrationCount) {
    }
}
