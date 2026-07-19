package com.serviceos.integration.application;

import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.integration.spi.CancelWorkOrderMappedInbound;
import com.serviceos.integration.spi.CancelWorkOrderRouteHint;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将冻结 INTEGRATION Mapping 输出物化为取消 Canonical 意图。
 *
 * <p>M339：{@code reasonCode} 必填（可用 constantValue）；{@code approvalRef} 可选；
 * businessKey / createBusinessKey 由 RouteHint 按 mapped externalOrderCode 重写。</p>
 */
public final class CancelWorkOrderMappingMaterializer {
    private CancelWorkOrderMappingMaterializer() {
    }

    public static CancelWorkOrderMappedInbound materialize(
            CancelWorkOrderRouteHint routeHint,
            IntegrationMappingResult mapping,
            ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(routeHint, "routeHint");
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(objectMapper, "objectMapper");
        if (!"INBOUND".equals(mapping.direction())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "CANCEL_WORK_ORDER materializer requires INBOUND INTEGRATION mapping");
        }

        Map<String, Object> fields = mapping.internalFields();
        String externalOrderCode = requiredField(fields, "externalOrderCode");
        String reasonCode = requiredField(fields, "reasonCode");
        String approvalRef = optionalText(fields, "approvalRef");
        String mappingVersionId = mapping.assetVersionId().toString();

        String createBusinessKey = CreateWorkOrderMappingMaterializer.rebuildBusinessKey(
                routeHint.createBusinessKey(), routeHint.externalOrderCode(), externalOrderCode);
        String businessKeyBase = CreateWorkOrderMappingMaterializer.rebuildBusinessKey(
                routeHint.businessKeyBase(), routeHint.externalOrderCode(), externalOrderCode);
        String businessKey = businessKeyBase + ":" + routeHint.businessKeySuffix();

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("mappingKey", mapping.mappingKey());
        canonical.put("mappingAssetVersionId", mappingVersionId);
        canonical.put("mappingContentDigest", mapping.contentDigest());
        canonical.put("connectorCode", mapping.connectorCode());
        canonical.put("direction", mapping.direction());
        canonical.put("messageType", CancelWorkOrderMappedInbound.MESSAGE_TYPE_CANCEL_WORK_ORDER);
        canonical.put("clientCode", routeHint.clientCode());
        canonical.put("businessKey", businessKey);
        canonical.put("createBusinessKey", createBusinessKey);
        canonical.put("externalOrderCode", externalOrderCode);
        canonical.put("reasonCode", reasonCode);
        if (approvalRef != null) {
            canonical.put("approvalRef", approvalRef);
        }
        canonical.put("explanations", mapping.explanations());

        byte[] canonicalPayload;
        try {
            canonicalPayload = objectMapper.writeValueAsBytes(canonical);
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Cannot serialize cancel mapping-materialized canonical payload: "
                            + exception.getMessage());
        }

        return new CancelWorkOrderMappedInbound(
                businessKey,
                createBusinessKey,
                externalOrderCode,
                routeHint.clientCode(),
                reasonCode,
                approvalRef,
                mappingVersionId,
                canonicalPayload);
    }

    private static String requiredField(Map<String, Object> fields, String key) {
        if (!fields.containsKey(key)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION mapping missing required field: " + key);
        }
        Object value = fields.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION mapping produced blank required field: " + key);
        }
        return String.valueOf(value).trim();
    }

    private static String optionalText(Map<String, Object> fields, String key) {
        if (!fields.containsKey(key)) {
            return null;
        }
        Object value = fields.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }
}
