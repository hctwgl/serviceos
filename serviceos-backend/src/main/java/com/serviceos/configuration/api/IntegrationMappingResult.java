package com.serviceos.configuration.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * INTEGRATION Mapping 运行时输出：字段、解释与版本锁定证据。
 *
 * <p>INBOUND：结果在 {@code internalFields}；OUTBOUND：结果在 {@code externalFields}。</p>
 */
public record IntegrationMappingResult(
        String mappingKey,
        UUID assetVersionId,
        String contentDigest,
        String connectorCode,
        String direction,
        Map<String, Object> internalFields,
        Map<String, Object> externalFields,
        List<String> explanations
) {
    public IntegrationMappingResult {
        internalFields = Map.copyOf(internalFields);
        externalFields = Map.copyOf(externalFields);
        explanations = List.copyOf(explanations);
    }
}
