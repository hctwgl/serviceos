package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.UiPreferenceCommandService;
import com.serviceos.readmodel.api.UiPreferenceQueryService;
import com.serviceos.readmodel.api.UiPreferenceWrite;
import com.serviceos.readmodel.api.UiPreferencesDocument;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.shared.ProblemCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin 个人 UI Preference HTTP 适配器。tenant/principal 只来自受信 CurrentPrincipal；
 * 不提供共享端点；portal 仅 ADMIN。
 */
@RestController
@RequestMapping("/api/v1")
final class UiPreferenceController {
    private final UiPreferenceQueryService queries;
    private final UiPreferenceCommandService commands;
    private final CurrentPrincipalProvider principals;

    UiPreferenceController(
            UiPreferenceQueryService queries,
            UiPreferenceCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @GetMapping("/me/ui-preferences")
    ResponseEntity<UiPreferencesDocument> get(
            @RequestParam("portal") String portal,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        UiPreferencesDocument document = queries.get(principals.current(), correlationId, portal);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(document);
    }

    @PutMapping("/me/ui-preferences")
    ResponseEntity<UiPreferencesDocument> put(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody PutUiPreferencesRequest request
    ) {
        if (request.preferences() == null || request.preferences().isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "preferences 不能为空");
        }
        Map<String, UiPreferenceWrite> writes = new LinkedHashMap<>();
        for (Map.Entry<String, PreferenceWriteBody> entry : request.preferences().entrySet()) {
            PreferenceWriteBody body = entry.getValue();
            if (body == null) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "偏好写入体不能为空");
            }
            writes.put(entry.getKey(), new UiPreferenceWrite(
                    body.value(), body.schemaVersion(), body.expectedVersion()));
        }
        UiPreferencesDocument document = commands.put(
                principals.current(),
                correlationId,
                request.portal(),
                writes);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(document);
    }

    @DeleteMapping("/me/ui-preferences/{key}")
    ResponseEntity<Void> delete(
            @PathVariable("key") String key,
            @RequestParam(value = "portal", defaultValue = "ADMIN") String portal,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        commands.delete(principals.current(), correlationId, portal, key);
        return ResponseEntity.noContent()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .build();
    }

    record PutUiPreferencesRequest(
            @NotBlank @Size(max = 32) String portal,
            @NotEmpty Map<String, @Valid PreferenceWriteBody> preferences
    ) {
    }

    record PreferenceWriteBody(
            @NotNull JsonNode value,
            @NotNull Integer schemaVersion,
            Long expectedVersion
    ) {
    }
}
