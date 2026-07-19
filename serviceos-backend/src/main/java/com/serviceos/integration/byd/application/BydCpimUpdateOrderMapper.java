package com.serviceos.integration.byd.application;

import com.serviceos.integration.spi.UpdateWorkOrderMappedInbound;
import com.serviceos.shared.Sha256;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** BYD 安装订单更新反腐映射；仅允许联系/地址字段，未知字段失败关闭。 */
final class BydCpimUpdateOrderMapper {
    static final String MAPPING_VERSION = "byd-ocean-shandong-update-order-v1";
    private static final Set<String> FIELDS = Set.of(
            "orderCode", "contactName", "contactMobile", "contactAddress",
            "provinceCode", "cityCode", "areaCode");

    UpdateWorkOrderMappedInbound map(Map<String, Object> source, ObjectMapper objectMapper) {
        if (!FIELDS.containsAll(source.keySet()) || !source.keySet().containsAll(FIELDS)) {
            throw new IllegalArgumentException("update payload fields are invalid");
        }
        String orderCode = required(source, "orderCode", 50);
        String contactName = required(source, "contactName", 128);
        String contactMobile = required(source, "contactMobile", 32);
        String contactAddress = required(source, "contactAddress", 512);
        String provinceCode = required(source, "provinceCode", 16);
        String cityCode = required(source, "cityCode", 16);
        String areaCode = required(source, "areaCode", 16);
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("orderCode", orderCode);
        canonical.put("contactName", contactName);
        canonical.put("contactMobile", contactMobile);
        canonical.put("contactAddress", contactAddress);
        canonical.put("provinceCode", provinceCode);
        canonical.put("cityCode", cityCode);
        canonical.put("areaCode", areaCode);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(canonical);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("update canonical serialization failed", exception);
        }
        String updateDigest = Sha256.digest(payload);
        return new UpdateWorkOrderMappedInbound(
                "BYD:INSTALL-UPDATE:" + orderCode + ":" + updateDigest,
                orderCode,
                "BYD",
                contactName,
                contactMobile,
                contactAddress,
                provinceCode,
                cityCode,
                areaCode,
                updateDigest,
                MAPPING_VERSION,
                payload);
    }

    private static String required(Map<String, Object> source, String field, int maximum) {
        Object raw = source.get(field);
        if (!(raw instanceof String value) || value.isBlank()
                || !value.equals(value.trim()) || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
