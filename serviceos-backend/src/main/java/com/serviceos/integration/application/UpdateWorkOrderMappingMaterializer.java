package com.serviceos.integration.application;

import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.integration.spi.UpdateWorkOrderMappedInbound;
import com.serviceos.integration.spi.UpdateWorkOrderRouteHint;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将冻结 INTEGRATION Mapping 输出物化为更新 Canonical 意图。
 *
 * <p>M339：领域更新字段仅取自 Mapping {@code internalFields}；{@code updateDigest}
 * 由 Canonical 字节计算；{@code mappingVersionId} 固定为资产 {@code assetVersionId}。</p>
 */
public final class UpdateWorkOrderMappingMaterializer {
    private UpdateWorkOrderMappingMaterializer() {
    }

    public static UpdateWorkOrderMappedInbound materialize(
            UpdateWorkOrderRouteHint routeHint,
            IntegrationMappingResult mapping,
            ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(routeHint, "routeHint");
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(objectMapper, "objectMapper");
        if (!"INBOUND".equals(mapping.direction())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "UPDATE_WORK_ORDER materializer requires INBOUND INTEGRATION mapping");
        }

        Map<String, Object> fields = mapping.internalFields();
        String externalOrderCode = requiredField(fields, "externalOrderCode");
        String customerName = requiredField(fields, "customerName");
        String customerMobile = requiredField(fields, "customerMobile");
        String serviceAddress = requiredField(fields, "serviceAddress");
        String provinceCode = requiredField(fields, "provinceCode");
        String cityCode = requiredField(fields, "cityCode");
        String districtCode = requiredField(fields, "districtCode");
        String mappingVersionId = mapping.assetVersionId().toString();

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("mappingKey", mapping.mappingKey());
        canonical.put("mappingAssetVersionId", mappingVersionId);
        canonical.put("mappingContentDigest", mapping.contentDigest());
        canonical.put("connectorCode", mapping.connectorCode());
        canonical.put("direction", mapping.direction());
        canonical.put("messageType", UpdateWorkOrderMappedInbound.MESSAGE_TYPE_UPDATE_WORK_ORDER);
        canonical.put("clientCode", routeHint.clientCode());
        canonical.put("externalOrderCode", externalOrderCode);
        canonical.put("customerName", customerName);
        canonical.put("customerMobile", customerMobile);
        canonical.put("serviceAddress", serviceAddress);
        canonical.put("provinceCode", provinceCode);
        canonical.put("cityCode", cityCode);
        canonical.put("districtCode", districtCode);
        canonical.put("explanations", mapping.explanations());

        byte[] canonicalPayload;
        try {
            canonicalPayload = objectMapper.writeValueAsBytes(canonical);
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Cannot serialize update mapping-materialized canonical payload: "
                            + exception.getMessage());
        }
        String updateDigest = Sha256.digest(canonicalPayload);
        String businessKeyBase = CreateWorkOrderMappingMaterializer.rebuildBusinessKey(
                routeHint.businessKeyBase(), routeHint.externalOrderCode(), externalOrderCode);
        String businessKey = businessKeyBase + ":" + updateDigest;

        return new UpdateWorkOrderMappedInbound(
                businessKey,
                externalOrderCode,
                routeHint.clientCode(),
                customerName,
                customerMobile,
                serviceAddress,
                provinceCode,
                cityCode,
                districtCode,
                updateDigest,
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
}
