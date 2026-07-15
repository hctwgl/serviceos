package com.serviceos.integration.byd.api;

import java.util.List;

/** BYD 2.6 厂端审核回调响应；失败列表只包含订单号和稳定原因码。 */
public record BydCpimReviewCallbackResponse(
        String message,
        List<Failure> data
) {
    public BydCpimReviewCallbackResponse {
        data = data == null ? List.of() : List.copyOf(data);
    }

    public static BydCpimReviewCallbackResponse rejected(String code) {
        return new BydCpimReviewCallbackResponse(code, List.of());
    }

    public record Failure(String orderCode, String reason) {
    }
}
