package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceSetSnapshotTaskCompletionValidatorExplainTest {

    @Test
    void explainsMissingRequiredEvidenceSlots() {
        EvidenceSetSnapshotRepository snapshots = mock(EvidenceSetSnapshotRepository.class);
        EvidenceSlotRepository slots = mock(EvidenceSlotRepository.class);
        TaskFulfillmentContextService tasks = mock(TaskFulfillmentContextService.class);
        EvidenceSetSnapshotTaskCompletionValidator validator =
                new EvidenceSetSnapshotTaskCompletionValidator(snapshots, slots, tasks);

        UUID taskId = UUID.randomUUID();
        when(slots.resolutionExists("t1", taskId)).thenReturn(true);
        when(slots.hasPendingDisposition("t1", taskId)).thenReturn(false);
        when(slots.listSlots("t1", taskId)).thenReturn(List.of(
                slot(taskId, "接地检测照片", true, "MISSING"),
                slot(taskId, "铭牌照片", true, "SATISFIED")));

        assertThat(validator.explainBlockingReasons("t1", taskId))
                .containsExactly("缺少必传资料：接地检测照片");
    }

    private static EvidenceSlotView slot(
            UUID taskId, String name, boolean required, String status
    ) {
        return new EvidenceSlotView(
                UUID.randomUUID(), UUID.randomUUID(), taskId, UUID.randomUUID(),
                UUID.randomUUID(), "tpl", "1.0.0", "d".repeat(64),
                "REQ", "occ-1", name, "IMAGE", required, 1, 3,
                null, null, "{}", "e".repeat(64), status, Instant.now());
    }
}
