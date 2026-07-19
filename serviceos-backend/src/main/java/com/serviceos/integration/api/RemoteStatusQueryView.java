package com.serviceos.integration.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 远端状态查询观察结果。
 *
 * <p>{@code outcome}：CONFIRMED_ACCEPTED / CONFIRMED_REJECTED / STILL_UNKNOWN / NOT_SUPPORTED。
 * 本切片不自动改写 Delivery 状态；人工仍可走 :retry。</p>
 */
public record RemoteStatusQueryView(
        UUID deliveryId,
        String connectorVersionId,
        String outcome,
        String reasonCode,
        String detail,
        String externalRef,
        Instant queriedAt
) {
}
