package com.serviceos.evidence.web;

import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 任务资料槽位 HTTP 边界；租户与主体只从受信 JWT 获取。 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/evidence-slots")
final class EvidenceSlotController {
    private final EvidenceSlotQueryService slots;
    private final CurrentPrincipalProvider principals;
    private final ObjectMapper objectMapper;

    EvidenceSlotController(
            EvidenceSlotQueryService slots,
            CurrentPrincipalProvider principals,
            ObjectMapper objectMapper
    ) {
        this.slots = slots;
        this.principals = principals;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    List<EvidenceSlotResponse> list(
            @PathVariable UUID taskId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return slots.listForTask(principals.current(), correlationId, taskId)
                .stream().map(this::response).toList();
    }

    private EvidenceSlotResponse response(EvidenceSlotView slot) {
        try {
            return new EvidenceSlotResponse(
                    slot.slotId(), slot.resolutionId(), slot.taskId(), slot.projectId(),
                    slot.templateVersionId(), slot.templateKey(), slot.templateVersion(),
                    slot.templateDigest(), slot.requirementCode(), slot.occurrenceKey(),
                    slot.requirementName(), slot.mediaType(), slot.required(), slot.minCount(),
                    slot.maxCount(), slot.conditionInputDigest(),
                    objectMapper.readTree(slot.resolutionExplanationJson()),
                    objectMapper.readTree(slot.requirementDefinitionJson()),
                    slot.requirementDigest(), slot.status(), slot.resolvedAt(),
                    slot.slotGeneration(), slot.supersedesSlotId(), slot.currentResolutionId(),
                    slot.resolutionGeneration(), slot.active(), slot.transition(),
                    slot.requiredDisposition());
        } catch (JacksonException exception) {
            // 发布与解析门禁已保证 JSON 合法；存量损坏必须失败关闭，不能返回伪造空约束。
            throw new IllegalStateException("EvidenceSlot frozen JSON is invalid", exception);
        }
    }

    record EvidenceSlotResponse(
            UUID slotId,
            UUID resolutionId,
            UUID taskId,
            UUID projectId,
            UUID templateVersionId,
            String templateKey,
            String templateVersion,
            String templateDigest,
            String requirementCode,
            String occurrenceKey,
            String requirementName,
            String mediaType,
            boolean required,
            int minCount,
            Integer maxCount,
            String conditionInputDigest,
            JsonNode resolutionExplanation,
            JsonNode requirement,
            String requirementDigest,
            String status,
            Instant resolvedAt,
            int slotGeneration,
            UUID supersedesSlotId,
            UUID currentResolutionId,
            int resolutionGeneration,
            boolean active,
            String transition,
            String requiredDisposition
    ) {
    }
}
