package com.serviceos.integration.byd.application;

import java.time.LocalDateTime;
import java.util.List;

/** 已通过严格协议校验的 BYD 厂端审核回调。 */
record BydCpimMappedReviewCallback(
        List<String> orderCodes,
        String externalResult,
        String domainResult,
        String remark,
        String examinePerson,
        String examineDateText,
        LocalDateTime examineDate
) {
    BydCpimMappedReviewCallback {
        orderCodes = List.copyOf(orderCodes);
    }
}
