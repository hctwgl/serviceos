package com.serviceos.integration.geely.api;

import java.util.List;

/**
 * 协议 7.1 安装单创建（加密前 data JSON）子集。
 *
 * <p>仅映射建单所需字段；其余字段保留在 canonical JSON 中供审计。</p>
 */
public record GeelyCreateOrderPayload(
        String installProcessNo,
        String workNo,
        String assignProviderTime,
        String applyTime,
        Integer status,
        String carOwnerName,
        String carOwnerPhone,
        String contactName,
        String contactPhone,
        String province,
        String city,
        String district,
        String address,
        String parkingLot,
        String carBrand,
        String carModel,
        String vin,
        Integer carryProduct,
        String userRemark,
        Integer licensePhotoSkip,
        List<Product> productList
) {
    public record Product(
            String productName,
            Double productPower,
            String packageInfo
    ) {
    }
}
