package com.serviceos.project.domain;

import com.serviceos.project.api.CreateProjectCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectTest {
    @Test
    void createNormalizesCodeAndStartsInDraft() {
        Project project = Project.create(
                "tenant-1",
                new CreateProjectCommand(" byd-2026 ", "client-byd", "比亚迪家充", LocalDate.of(2026, 1, 1), null,
                        java.util.List.of("CN-3702")),
                UUID.fromString("1fa0cbe4-4b86-48b6-a495-6c86c7d1e901"),
                Instant.parse("2026-07-13T03:30:00Z"));

        assertThat(project.code()).isEqualTo("BYD-2026");
        assertThat(project.status()).isEqualTo(Project.Status.DRAFT);
        assertThat(project.version()).isEqualTo(1L);
    }

    @Test
    void endDateCannotPrecedeStartDate() {
        CreateProjectCommand command = new CreateProjectCommand(
                "BYD-2026", "client-byd", "比亚迪家充",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), java.util.List.of());

        assertThatThrownBy(() -> Project.create("tenant-1", command, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endsOn");
    }

    @Test
    void codeCannotContainCharactersOutsideThePublishedContract() {
        CreateProjectCommand command = new CreateProjectCommand(
                "BYD/2026", "client-byd", "比亚迪家充",
                LocalDate.of(2026, 1, 1), null, java.util.List.of());

        assertThatThrownBy(() -> Project.create("tenant-1", command, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    void regionReferencesRejectDuplicatesAndBoundaryWhitespaceInsteadOfNormalizingThem() {
        CreateProjectCommand duplicate = new CreateProjectCommand(
                "BYD-2026", "client-byd", "比亚迪家充", LocalDate.of(2026, 1, 1), null,
                java.util.List.of("CN-3702", "CN-3702"));
        CreateProjectCommand whitespace = new CreateProjectCommand(
                "BYD-2027", "client-byd", "比亚迪家充", LocalDate.of(2027, 1, 1), null,
                java.util.List.of(" CN-3702"));

        assertThatThrownBy(() -> Project.create("tenant-1", duplicate, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> Project.create("tenant-1", whitespace, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid stable reference");
    }
}
