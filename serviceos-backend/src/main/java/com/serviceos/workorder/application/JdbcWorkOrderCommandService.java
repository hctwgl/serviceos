package com.serviceos.workorder.application;

import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderReceipt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
final class JdbcWorkOrderCommandService implements WorkOrderCommandService {
    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcWorkOrderCommandService(JdbcClient jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    JdbcWorkOrderCommandService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WorkOrderReceipt receive(ReceiveExternalWorkOrderCommand command) {
        UUID id = UUID.randomUUID();
        Instant receivedAt = clock.instant();
        int inserted = jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, client_code, brand_code, service_product_code, external_order_code,
                    payload_digest, status, configuration_bundle_code, configuration_bundle_version,
                    province_code, city_code, district_code, customer_name, customer_mobile,
                    service_address, vehicle_vin, external_dispatched_at, received_at, version
                ) VALUES (
                    :id, :clientCode, :brandCode, :serviceProductCode, :externalOrderCode,
                    :payloadDigest, 'RECEIVED', :bundleCode, :bundleVersion,
                    :provinceCode, :cityCode, :districtCode, :customerName, :customerMobile,
                    :serviceAddress, :vehicleVin, :externalDispatchedAt, :receivedAt, 1
                ) ON CONFLICT (client_code, external_order_code) DO NOTHING
                """)
                .param("id", id)
                .param("clientCode", command.clientCode())
                .param("brandCode", command.brandCode())
                .param("serviceProductCode", command.serviceProductCode())
                .param("externalOrderCode", command.externalOrderCode())
                .param("payloadDigest", command.payloadDigest())
                .param("bundleCode", command.configurationBundleCode())
                .param("bundleVersion", command.configurationBundleVersion())
                .param("provinceCode", command.provinceCode())
                .param("cityCode", command.cityCode())
                .param("districtCode", command.districtCode())
                .param("customerName", command.customerName())
                .param("customerMobile", command.customerMobile())
                .param("serviceAddress", command.serviceAddress())
                .param("vehicleVin", command.vehicleVin())
                .param("externalDispatchedAt", command.externalDispatchedAt())
                .param("receivedAt", java.sql.Timestamp.from(receivedAt))
                .update();

        if (inserted == 1) {
            return receipt(id, command, false, receivedAt);
        }

        Existing existing = jdbc.sql("""
                SELECT id, payload_digest, status, configuration_bundle_code,
                       configuration_bundle_version, received_at
                  FROM wo_work_order
                 WHERE client_code = :clientCode AND external_order_code = :externalOrderCode
                """)
                .param("clientCode", command.clientCode())
                .param("externalOrderCode", command.externalOrderCode())
                .query((rs, rowNum) -> new Existing(
                        rs.getObject("id", UUID.class),
                        rs.getString("payload_digest"),
                        rs.getString("status"),
                        rs.getString("configuration_bundle_code"),
                        rs.getString("configuration_bundle_version"),
                        rs.getTimestamp("received_at").toInstant()))
                .single();

        if (!existing.payloadDigest().equals(command.payloadDigest())) {
            throw new IllegalStateException("external order already exists with a different payload");
        }
        if (!existing.bundleCode().equals(command.configurationBundleCode())
                || !existing.bundleVersion().equals(command.configurationBundleVersion())) {
            throw new IllegalStateException("external order replay attempted with a different configuration bundle");
        }
        return new WorkOrderReceipt(existing.id(), command.externalOrderCode(), existing.status(),
                existing.bundleCode(), existing.bundleVersion(), true, existing.receivedAt());
    }

    private static WorkOrderReceipt receipt(
            UUID id, ReceiveExternalWorkOrderCommand command, boolean replay, Instant receivedAt) {
        return new WorkOrderReceipt(id, command.externalOrderCode(), "RECEIVED",
                command.configurationBundleCode(), command.configurationBundleVersion(), replay, receivedAt);
    }

    private record Existing(
            UUID id, String payloadDigest, String status,
            String bundleCode, String bundleVersion, Instant receivedAt) {
    }
}
