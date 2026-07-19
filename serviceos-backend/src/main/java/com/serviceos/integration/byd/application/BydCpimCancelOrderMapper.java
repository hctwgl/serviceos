package com.serviceos.integration.byd.application;

import com.serviceos.integration.byd.api.BydCpimCancelOrderPayload;
import com.serviceos.integration.spi.CancelWorkOrderMappedInbound;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Set;

/** BYD 取消订单反腐映射；缺字段/未知字段/宽松格式均失败关闭。 */
final class BydCpimCancelOrderMapper {
    static final String MAPPING_VERSION = "byd-ocean-shandong-cancel-order-v1";
    private static final Set<String> FIELDS = Set.of("orderCode", "cancelDate", "cancelReason");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    CancelWorkOrderMappedInbound map(Map<String, Object> source, ObjectMapper objectMapper) {
        if (!FIELDS.containsAll(source.keySet()) || !source.keySet().containsAll(FIELDS)) {
            throw new IllegalArgumentException("cancel payload fields are invalid");
        }
        String orderCode = required(source, "orderCode", 50);
        String cancelDate = required(source, "cancelDate", 50);
        String cancelReason = required(source, "cancelReason", 64);
        try {
            LocalDateTime.parse(cancelDate, DATE_TIME);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("cancelDate must use yyyy-MM-dd HH:mm:ss", exception);
        }
        byte[] canonical;
        try {
            canonical = objectMapper.writeValueAsBytes(new BydCpimCancelOrderPayload(
                    orderCode, cancelDate, cancelReason));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("cancel canonical serialization failed", exception);
        }
        return new CancelWorkOrderMappedInbound(
                "BYD:CANCEL:" + orderCode + ":" + cancelDate,
                "BYD:INSTALL:" + orderCode,
                orderCode,
                "BYD",
                "EXTERNAL_USER_CANCEL",
                "BYD:" + cancelReason,
                MAPPING_VERSION,
                canonical);
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
