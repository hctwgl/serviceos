package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceSetSnapshotValidatorTest {
    private final EvidenceSetSnapshotValidator validator =
            new EvidenceSetSnapshotValidator(JsonMapper.builder().build());

    @Test
    void rejectsUnsupportedPurposeAndNonValidatedRevision() {
        UUID taskId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        EvidenceSlotView slot = slot(slotId, taskId, 1, 2);
        EvidenceRevisionView revision = revision(revisionId, taskId, slotId, "STORED");

        assertThatThrownBy(() -> validator.validate(
                taskId, "REVIEW", List.of(revisionId), List.of(revision), List.of(slot)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.EVIDENCE_SNAPSHOT_PURPOSE_UNSUPPORTED));

        assertThatThrownBy(() -> validator.validate(
                taskId, "TASK_SUBMISSION", List.of(revisionId), List.of(revision), List.of(slot)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.EVIDENCE_NOT_READY_FOR_REVIEW));
    }

    @Test
    void rejectsIncompleteRequiredCoverage() {
        UUID taskId = UUID.randomUUID();
        UUID firstSlot = UUID.randomUUID();
        UUID secondSlot = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        EvidenceSlotView slotA = slot(firstSlot, taskId, 1, 2);
        EvidenceSlotView slotB = new EvidenceSlotView(
                secondSlot, slotA.resolutionId(), taskId, slotA.projectId(), slotA.templateVersionId(),
                "survey.site", "1.0.0", "b".repeat(64), "nameplate.photo", "default",
                "铭牌", "PHOTO", true, 1, 1, "c".repeat(64), "{\"kind\":\"FIXED\"}",
                "{\"evidenceKey\":\"nameplate.photo\",\"mediaType\":\"PHOTO\",\"required\":true}",
                "d".repeat(64), "MISSING", Instant.parse("2026-07-14T08:00:00Z"));
        EvidenceRevisionView revision = revision(revisionId, taskId, firstSlot, "VALIDATED");

        assertThatThrownBy(() -> validator.validate(
                taskId, "TASK_SUBMISSION", List.of(revisionId), List.of(revision),
                List.of(slotA, slotB)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.EVIDENCE_SNAPSHOT_INCOMPLETE));
    }

    @Test
    void acceptsValidatedCoverage() {
        UUID taskId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        EvidenceSlotView slot = slot(slotId, taskId, 1, 2);
        EvidenceRevisionView revision = revision(revisionId, taskId, slotId, "VALIDATED");

        var validated = validator.validate(
                taskId, "TASK_SUBMISSION", List.of(revisionId), List.of(revision), List.of(slot));
        assertThat(validated.revisions()).hasSize(1);
        assertThat(validated.resolutionId()).isEqualTo(slot.resolutionId());
    }

    private static EvidenceSlotView slot(UUID slotId, UUID taskId, int min, Integer max) {
        return new EvidenceSlotView(
                slotId, UUID.randomUUID(), taskId, UUID.randomUUID(), UUID.randomUUID(),
                "survey.site", "1.0.0", "b".repeat(64), "site.photo", "default",
                "现场照片", "PHOTO", true, min, max, "c".repeat(64), "{\"kind\":\"FIXED\"}",
                "{\"evidenceKey\":\"site.photo\",\"mediaType\":\"PHOTO\",\"required\":true}",
                "d".repeat(64), "MISSING", Instant.parse("2026-07-14T08:00:00Z"));
    }

    private static EvidenceRevisionView revision(
            UUID revisionId, UUID taskId, UUID slotId, String status
    ) {
        return new EvidenceRevisionView(
                revisionId, UUID.randomUUID(), slotId, taskId, UUID.randomUUID(), 1,
                UUID.randomUUID(), "a".repeat(64), "image/png", 12,
                "{\"captureSource\":\"CAMERA\"}", status, UUID.randomUUID(), "cmd",
                "tech", Instant.parse("2026-07-14T09:00:00Z"), List.of());
    }
}
