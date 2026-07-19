package com.serviceos.integration.application;

import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将冻结 INTEGRATION Mapping 输出物化为建单 Canonical 意图。
 *
 * <p>M333：一旦进入物化，领域建单字段仅取自 Mapping {@code internalFields}，不再回退适配器
 * 兼容取值。{@code clientCode} 仍来自 connector 身份；{@code businessKey} 仅按 Mapping 的
 * {@code externalOrderCode} 重写后缀。{@code mappingVersionId} 固定为资产
 * {@code assetVersionId}；Canonical JSON 嵌入 {@code mappingContentDigest}。</p>
 */
public final class CreateWorkOrderMappingMaterializer {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME_T = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private CreateWorkOrderMappingMaterializer() {
    }

    public static CreateWorkOrderMappedInbound materialize(
            CreateWorkOrderMappedInbound adapterMapped,
            IntegrationMappingResult mapping,
            ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(adapterMapped, "adapterMapped");
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(objectMapper, "objectMapper");
        if (!"INBOUND".equals(mapping.direction())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "CREATE_WORK_ORDER materializer requires INBOUND INTEGRATION mapping");
        }

        Map<String, Object> fields = mapping.internalFields();
        String externalOrderCode = requiredField(fields, "externalOrderCode");
        String brandCode = requiredField(fields, "brandCode");
        String serviceProductCode = requiredField(fields, "serviceProductCode");
        String provinceCode = requiredField(fields, "provinceCode");
        String cityCode = requiredField(fields, "cityCode");
        String districtCode = requiredField(fields, "districtCode");
        String customerName = optionalText(fields, "customerName");
        String customerMobile = optionalText(fields, "customerMobile");
        String serviceAddress = optionalText(fields, "serviceAddress");
        String vehicleVin = optionalText(fields, "vehicleVin");
        LocalDateTime dispatchedAt = overlayDispatchedAt(fields);

        String businessKey = rebuildBusinessKey(
                adapterMapped.businessKey(), adapterMapped.externalOrderCode(), externalOrderCode);
        String mappingVersionId = mapping.assetVersionId().toString();

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("mappingKey", mapping.mappingKey());
        canonical.put("mappingAssetVersionId", mappingVersionId);
        canonical.put("mappingContentDigest", mapping.contentDigest());
        canonical.put("connectorCode", mapping.connectorCode());
        canonical.put("direction", mapping.direction());
        canonical.put("clientCode", adapterMapped.clientCode());
        canonical.put("businessKey", businessKey);
        canonical.put("externalOrderCode", externalOrderCode);
        canonical.put("brandCode", brandCode);
        canonical.put("serviceProductCode", serviceProductCode);
        canonical.put("provinceCode", provinceCode);
        canonical.put("cityCode", cityCode);
        canonical.put("districtCode", districtCode);
        if (customerName != null) {
            canonical.put("customerName", customerName);
        }
        if (customerMobile != null) {
            canonical.put("customerMobile", customerMobile);
        }
        if (serviceAddress != null) {
            canonical.put("serviceAddress", serviceAddress);
        }
        if (vehicleVin != null) {
            canonical.put("vehicleVin", vehicleVin);
        }
        if (dispatchedAt != null) {
            canonical.put("dispatchedAt", dispatchedAt.toString());
        }
        canonical.put("explanations", mapping.explanations());

        byte[] canonicalPayload;
        try {
            canonicalPayload = objectMapper.writeValueAsBytes(canonical);
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Cannot serialize mapping-materialized canonical payload: "
                            + exception.getMessage());
        }

        return new CreateWorkOrderMappedInbound(
                businessKey,
                externalOrderCode,
                adapterMapped.clientCode(),
                brandCode,
                serviceProductCode,
                provinceCode,
                cityCode,
                districtCode,
                customerName,
                customerMobile,
                serviceAddress,
                vehicleVin,
                dispatchedAt,
                mappingVersionId,
                canonicalPayload);
    }

    static String rebuildBusinessKey(String adapterBusinessKey, String adapterOrderCode, String newOrderCode) {
        if (adapterBusinessKey != null && adapterOrderCode != null
                && adapterBusinessKey.endsWith(adapterOrderCode)) {
            return adapterBusinessKey.substring(0, adapterBusinessKey.length() - adapterOrderCode.length())
                    + newOrderCode;
        }
        throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                "Cannot rebuild businessKey after INTEGRATION mapping materialization");
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

    private static LocalDateTime overlayDispatchedAt(Map<String, Object> fields) {
        if (!fields.containsKey("dispatchedAt")) {
            return null;
        }
        Object value = fields.get("dispatchedAt");
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        String text = String.valueOf(value).trim();
        try {
            return LocalDateTime.parse(text, DATE_TIME_T);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(text, DATE).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "INTEGRATION mapping dispatchedAt is not a supported date-time: " + text);
        }
    }
}
