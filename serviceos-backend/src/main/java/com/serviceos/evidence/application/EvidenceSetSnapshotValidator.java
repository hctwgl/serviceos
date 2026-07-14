package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** TASK_SUBMISSION 用途的成员资格与槽位覆盖校验。 */
final class EvidenceSetSnapshotValidator {
    static final String PURPOSE_TASK_SUBMISSION = "TASK_SUBMISSION";

    private final ObjectMapper objectMapper;

    EvidenceSetSnapshotValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ValidatedMembers validate(
            UUID taskId,
            String purpose,
            List<UUID> requestedRevisionIds,
            List<EvidenceRevisionView> loadedRevisions,
            List<EvidenceSlotView> slots
    ) {
        if (purpose == null || purpose.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "purpose must not be blank");
        }
        if (!PURPOSE_TASK_SUBMISSION.equals(purpose)) {
            throw new BusinessProblem(ProblemCode.EVIDENCE_SNAPSHOT_PURPOSE_UNSUPPORTED,
                    "EvidenceSetSnapshot purpose is not supported in M40: " + purpose);
        }
        if (requestedRevisionIds == null || requestedRevisionIds.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "memberRevisionIds must not be empty");
        }
        if (slots.isEmpty()) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Task has no resolved EvidenceSlots");
        }

        Set<UUID> requested = new HashSet<>();
        for (UUID revisionId : requestedRevisionIds) {
            if (revisionId == null) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "memberRevisionIds must not contain null");
            }
            if (!requested.add(revisionId)) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "memberRevisionIds must not contain duplicates");
            }
        }

        Map<UUID, EvidenceRevisionView> byId = new LinkedHashMap<>();
        for (EvidenceRevisionView revision : loadedRevisions) {
            byId.put(revision.evidenceRevisionId(), revision);
        }
        if (byId.size() != requested.size()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "One or more EvidenceRevisions do not exist");
        }

        Map<UUID, EvidenceSlotView> slotsById = new HashMap<>();
        for (EvidenceSlotView slot : slots) {
            slotsById.put(slot.slotId(), slot);
        }

        Set<UUID> items = new HashSet<>();
        List<EvidenceRevisionView> ordered = new ArrayList<>();
        for (UUID revisionId : requestedRevisionIds) {
            EvidenceRevisionView revision = byId.get(revisionId);
            if (!taskId.equals(revision.taskId())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "EvidenceRevision does not belong to the Task");
            }
            if (!"VALIDATED".equals(revision.status())) {
                throw new BusinessProblem(ProblemCode.EVIDENCE_NOT_READY_FOR_REVIEW,
                        "EvidenceRevision is not VALIDATED for TASK_SUBMISSION");
            }
            if (!slotsById.containsKey(revision.evidenceSlotId())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "EvidenceRevision slot is not part of the Task resolution");
            }
            if (!items.add(revision.evidenceItemId())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "Snapshot may include at most one revision per EvidenceItem");
            }
            ordered.add(revision);
        }

        Map<UUID, Integer> countsBySlot = new HashMap<>();
        for (EvidenceRevisionView revision : ordered) {
            countsBySlot.merge(revision.evidenceSlotId(), 1, Integer::sum);
        }
        for (EvidenceSlotView slot : slots) {
            int count = countsBySlot.getOrDefault(slot.slotId(), 0);
            if (count < slot.minCount()) {
                throw new BusinessProblem(ProblemCode.EVIDENCE_SNAPSHOT_INCOMPLETE,
                        "Required EvidenceSlot is under-covered: " + slot.requirementCode());
            }
            if (slot.maxCount() != null && count > slot.maxCount()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "EvidenceSlot exceeds maxCount: " + slot.requirementCode());
            }
        }

        ordered.sort(Comparator
                .comparing(EvidenceRevisionView::evidenceSlotId)
                .thenComparing(EvidenceRevisionView::evidenceItemId)
                .thenComparingInt(EvidenceRevisionView::revisionNumber));
        return new ValidatedMembers(ordered, slots.getFirst().resolutionId());
    }

    String validationDigest(List<EvidenceValidationView> validations) {
        List<EvidenceValidationView> sorted = validations.stream()
                .sorted(Comparator.comparing(EvidenceValidationView::checkType)
                        .thenComparing(EvidenceValidationView::validatorVersion)
                        .thenComparing(EvidenceValidationView::result))
                .toList();
        ArrayNode array = objectMapper.createArrayNode();
        for (EvidenceValidationView validation : sorted) {
            ObjectNode node = array.addObject();
            node.put("checkType", validation.checkType());
            node.put("severity", validation.severity());
            node.put("result", validation.result());
            node.put("validatorVersion", validation.validatorVersion());
            if (validation.reasonCode() != null) {
                node.put("reasonCode", validation.reasonCode());
            }
        }
        return Sha256.digest(write(array));
    }

    String contentDigest(
            UUID taskId,
            UUID resolutionId,
            String purpose,
            List<EvidenceSetSnapshotMemberView> members
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("taskId", taskId.toString());
        root.put("purpose", purpose);
        root.put("resolutionId", resolutionId.toString());
        ArrayNode membersNode = root.putArray("members");
        for (EvidenceSetSnapshotMemberView member : members) {
            ObjectNode node = membersNode.addObject();
            node.put("evidenceRevisionId", member.evidenceRevisionId().toString());
            node.put("evidenceItemId", member.evidenceItemId().toString());
            node.put("slotId", member.evidenceSlotId().toString());
            node.put("revisionNumber", member.revisionNumber());
            node.put("contentDigest", member.contentDigest());
            node.put("validationDigest", member.validationDigest());
        }
        return Sha256.digest(write(root));
    }

    String eligibilitySummaryJson(String purpose, int memberCount) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("purpose", purpose);
        node.put("status", "ELIGIBLE");
        node.put("memberCount", memberCount);
        node.put("revisionStatusRequired", "VALIDATED");
        return write(node);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("EvidenceSetSnapshot JSON serialization failed", exception);
        }
    }

    record ValidatedMembers(List<EvidenceRevisionView> revisions, UUID resolutionId) {
    }
}
