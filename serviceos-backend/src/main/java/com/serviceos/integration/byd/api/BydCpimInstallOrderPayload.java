package com.serviceos.integration.byd.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BYD CPIM V7.3.1 安装订单推送的首个试点载荷。
 *
 * <p>仅固化“海洋品牌（40）+ 山东省（370000）+ 勘安”已确认字段；未确认扩展字段保留在原始报文，
 * 不直接渗透到领域模型。</p>
 */
public record BydCpimInstallOrderPayload(
        String orderCode,
        String contactName,
        String contactMobile,
        String contactAddress,
        String provinceCode,
        String provinceName,
        String cityCode,
        String cityName,
        String areaCode,
        String areaName,
        String wallboxName,
        String wallboxPower,
        String bringWallbox,
        LocalDateTime dispatchTime,
        String carOwnerType,
        String type,
        String carBrand,
        String carSeries,
        String carModel,
        String vin,
        String dealerName,
        String rightCode,
        BigDecimal orderAmount,
        String source,
        String channel
) {
    public BydCpimInstallOrderPayload {
        orderCode = requireText(orderCode, "orderCode", 64);
        contactName = requireText(contactName, "contactName", 64);
        contactMobile = requireText(contactMobile, "contactMobile", 32);
        contactAddress = requireText(contactAddress, "contactAddress", 512);
        provinceCode = requireText(provinceCode, "provinceCode", 16);
        provinceName = requireText(provinceName, "provinceName", 32);
        cityCode = requireText(cityCode, "cityCode", 16);
        cityName = requireText(cityName, "cityName", 32);
        areaCode = requireText(areaCode, "areaCode", 16);
        areaName = requireText(areaName, "areaName", 32);
        wallboxName = requireText(wallboxName, "wallboxName", 128);
        wallboxPower = requireText(wallboxPower, "wallboxPower", 32);
        bringWallbox = requireText(bringWallbox, "bringWallbox", 8);
        if (dispatchTime == null) {
            throw new IllegalArgumentException("dispatchTime must not be null");
        }
        carOwnerType = requireText(carOwnerType, "carOwnerType", 8);
        type = requireText(type, "type", 8);
        carBrand = requireText(carBrand, "carBrand", 8);
        carSeries = requireText(carSeries, "carSeries", 64);
        carModel = requireText(carModel, "carModel", 128);
        vin = requireText(vin, "vin", 32).toUpperCase(java.util.Locale.ROOT);
        dealerName = requireText(dealerName, "dealerName", 128);
        rightCode = requireText(rightCode, "rightCode", 64);
        if (orderAmount == null || orderAmount.signum() < 0) {
            throw new IllegalArgumentException("orderAmount must be non-negative");
        }
        source = requireText(source, "source", 8);
        channel = requireText(channel, "channel", 32);

        if (!"40".equals(carBrand)) {
            throw new IllegalArgumentException("pilot only accepts BYD Ocean carBrand=40");
        }
        if (!"370000".equals(provinceCode) || !"山东省".equals(provinceName)) {
            throw new IllegalArgumentException("pilot only accepts Shandong province");
        }
        if (!vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            throw new IllegalArgumentException("vin must be a valid 17-character vehicle identifier");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds max length " + maxLength);
        }
        return normalized;
    }
}
