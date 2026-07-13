package com.serviceos;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-002/003/004：任何循环、未声明依赖或跨模块访问内部包都会使构建失败。
 */
class ArchitectureTest {
    private final ApplicationModules modules = ApplicationModules.of(ServiceOsApplication.class);

    @Test
    void moduleBoundariesMustBeValid() {
        modules.verify();
    }

    @Test
    void firstEngineeringSliceMustExposeExpectedModules() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()).toList())
                .contains(
                        "shared", "bootstrap", "identity", "authorization",
                        "reliability", "audit", "project", "configuration", "task",
                        "operations", "files", "integration", "workorder", "workflow");
    }
}
