package com.serviceos.configuration.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * INTEGRATION Mapping 运行时输出：内部字段、解释与版本锁定证据。
 */
public record IntegrationMappingResult(
        String mappingKey,
        UUID assetVersionId,
        String contentDigest,
        String connectorCode,
        String direction,
        Map<String, Object> internalFields,
        List<String> explanations
) {
    public IntegrationMappingResult {
        internalFields = Map.copyOf(internalFields);
        explanations = List.copyOf(explanations);
    }
}
