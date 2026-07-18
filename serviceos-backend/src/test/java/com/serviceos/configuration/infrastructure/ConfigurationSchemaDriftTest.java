package com.serviceos.configuration.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 保证运行时内嵌 Schema 与架构事实源逐字节一致，防止复制资源静默漂移。 */
class ConfigurationSchemaDriftTest {

    @Test
    void embeddedFormSchemaMatchesArchitectureSource() throws IOException {
        Path repository = repositoryRoot();
        Path architectureSchema = repository.resolve(
                "serviceos-architecture/configuration/schemas/form.schema.json");
        Path runtimeSchema = repository.resolve(
                "serviceos-backend/src/main/resources/configuration-schemas/form-v1.schema.json");

        assertThat(Files.mismatch(architectureSchema, runtimeSchema)).isEqualTo(-1L);
    }

    @Test
    void embeddedEvidenceSchemaMatchesArchitectureSource() throws IOException {
        Path repository = repositoryRoot();
        Path architectureSchema = repository.resolve(
                "serviceos-architecture/configuration/schemas/evidence.schema.json");
        Path runtimeSchema = repository.resolve(
                "serviceos-backend/src/main/resources/configuration-schemas/evidence-v1.schema.json");

        assertThat(Files.mismatch(architectureSchema, runtimeSchema)).isEqualTo(-1L);
    }

    @Test
    void embeddedWorkflowSchemaMatchesArchitectureSource() throws IOException {
        Path repository = repositoryRoot();
        Path architectureSchema = repository.resolve(
                "serviceos-architecture/configuration/schemas/workflow.schema.json");
        Path runtimeSchema = repository.resolve(
                "serviceos-backend/src/main/resources/configuration-schemas/workflow-v1.schema.json");

        assertThat(Files.mismatch(architectureSchema, runtimeSchema)).isEqualTo(-1L);
    }

    @Test
    void embeddedHomeChargingTemplateMatchesArchitectureSource() throws IOException {
        assertTemplateSynced("home-charging-survey-install");
    }

    @Test
    void embeddedChargerServiceTemplatesMatchArchitectureSource() throws IOException {
        assertTemplateSynced("charger-maintenance");
        assertTemplateSynced("charger-relocate");
        assertTemplateSynced("charger-inspection");
    }

    private void assertTemplateSynced(String templateDir) throws IOException {
        Path repository = repositoryRoot();
        for (String file : java.util.List.of("workflow.json", "sla.json")) {
            Path architecture = repository.resolve(
                    "serviceos-architecture/configuration/templates/" + templateDir + "/" + file);
            Path runtime = repository.resolve(
                    "serviceos-backend/src/main/resources/configuration-templates/" + templateDir + "/" + file);
            assertThat(Files.mismatch(architecture, runtime))
                    .as(templateDir + "/" + file)
                    .isEqualTo(-1L);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("serviceos-architecture"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("serviceos-architecture"))) {
            return parent;
        }
        throw new IllegalStateException("cannot locate ServiceOS repository root from " + current);
    }
}
