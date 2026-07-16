package com.serviceos.integration.application;

import com.serviceos.integration.api.DeliveryTimelineContext;
import com.serviceos.integration.api.DeliveryTimelineContextQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
final class DefaultDeliveryTimelineContextQuery implements DeliveryTimelineContextQuery {
    private final OutboundDeliveryRepository deliveries;

    DefaultDeliveryTimelineContextQuery(OutboundDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliveryTimelineContext> find(String tenantId, UUID deliveryId) {
        return deliveries.findTimelineContext(tenantId, deliveryId);
    }
}
