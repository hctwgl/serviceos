package com.serviceos.integration.api;

import java.util.Optional;
import java.util.UUID;

/** 仅供其他模块按 tenant + deliveryId 解析稳定资源关系，不提供用户授权。 */
public interface DeliveryTimelineContextQuery {
    Optional<DeliveryTimelineContext> find(String tenantId, UUID deliveryId);
}
