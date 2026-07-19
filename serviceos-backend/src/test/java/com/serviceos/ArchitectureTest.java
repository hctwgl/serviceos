package com.serviceos;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-002/003/004：任何循环、未声明依赖或跨模块访问内部包都会使构建失败。
 *
 * <p>M267 起额外阻断核心履约模块对 OEM 适配包的源码引用，防止车企协议分叉渗入领域内核。</p>
 */
class ArchitectureTest {
    private static final List<String> CORE_DOMAIN_PACKAGES = List.of(
            "workorder", "workflow", "task", "dispatch", "sla", "forms", "evidence",
            "fieldwork", "appointment", "project", "configuration", "operations");

    private static final List<String> FORBIDDEN_OEM_ADAPTER_PACKAGES = List.of(
            "com.serviceos.integration.byd",
            "com.serviceos.integration.referenceoem");

    private final ApplicationModules modules = ApplicationModules.of(ServiceOsApplication.class);

    @Test
    void moduleBoundariesMustBeValid() {
        modules.verify();
    }

    @Test
    void firstEngineeringSliceMustExposeExpectedModules() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()).toList())
                .contains(
                        "shared", "bootstrap", "identity", "authorization", "organization", "network",
                        "reliability", "audit", "project", "configuration", "task",
                        "operations", "files", "integration", "workorder", "workflow",
                        "evidence");
    }

    @Test
    void integrationModuleMustExposeConnectorSpi() {
        ApplicationModule integration = modules.getModuleByName("integration").orElseThrow();
        assertThat(integration.getNamedInterfaces().stream().map(Object::toString).toList().toString())
                .contains("spi");
        assertThat(Files.exists(Path.of("src/main/java/com/serviceos/integration/spi/ConnectorIdentity.java")))
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/serviceos/integration/application/InboundCreateWorkOrderPipeline.java")))
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/serviceos/integration/spi/OutboundSubmissionConnector.java")))
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/serviceos/integration/application/OutboundSubmissionPipeline.java")))
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/serviceos/integration/spi/ReviewCallbackMappedItem.java")))
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/serviceos/integration/application/InboundReviewCallbackItemPipeline.java")))
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/serviceos/integration/spi/OutboundReviewSubmissionProfile.java")))
                .isTrue();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/serviceos/integration/application/OutboundReviewSubmissionProfiles.java")))
                .isTrue();
    }

    @Test
    void coreDomainSourcesMustNotImportOemAdapters() throws IOException {
        Path root = Path.of("src/main/java/com/serviceos");
        List<String> violations = new ArrayList<>();
        for (String module : CORE_DOMAIN_PACKAGES) {
            Path moduleRoot = root.resolve(module);
            if (!Files.isDirectory(moduleRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(moduleRoot)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> collectOemImportViolations(path, module, violations));
            }
        }
        assertThat(violations)
                .as("核心域不得 import OEM 适配包: %s", FORBIDDEN_OEM_ADAPTER_PACKAGES)
                .isEmpty();
    }

    private static void collectOemImportViolations(Path path, String module, List<String> violations) {
        try {
            String source = Files.readString(path);
            for (String forbidden : FORBIDDEN_OEM_ADAPTER_PACKAGES) {
                if (source.contains("import " + forbidden) || source.contains(forbidden + ".")) {
                    violations.add(module + " -> " + path + " references " + forbidden);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }
}
