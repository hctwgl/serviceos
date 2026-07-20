package com.serviceos.workorder.application;

import com.serviceos.jooq.generated.tables.WoWorkOrder;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ActivateWorkOrderCommand;
import com.serviceos.workorder.api.CancelWorkOrderCommand;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.FulfillWorkOrderCommand;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.ReopenWorkOrderCommand;
import com.serviceos.workorder.api.UpdateExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderActivatedPayload;
import com.serviceos.workorder.api.WorkOrderActivationReceipt;
import com.serviceos.workorder.api.WorkOrderCancellationReceipt;
import com.serviceos.workorder.api.WorkOrderCancelledPayload;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderExternalDetailsUpdatedPayload;
import com.serviceos.workorder.api.WorkOrderFulfilledPayload;
import com.serviceos.workorder.api.WorkOrderFulfillmentReceipt;
import com.serviceos.workorder.api.WorkOrderReceipt;
import com.serviceos.workorder.api.WorkOrderReceivedPayload;
import com.serviceos.workorder.api.WorkOrderReopenReceipt;
import com.serviceos.workorder.api.WorkOrderReopenedPayload;
import com.serviceos.workorder.api.WorkOrderUpdateReceipt;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WoWorkOrder.WO_WORK_ORDER;

/** WorkOrder 当前事实、业务幂等以及领域事件原子提交的唯一命令实现（jOOQ 实现）。 */
@Service
final class JooqWorkOrderCommandService implements WorkOrderCommandService {
    private static final String RECEIVED_EVENT = "workorder.received";
    private static final String ACTIVATED_EVENT = "workorder.activated";
    private static final String FULFILLED_EVENT = "workorder.fulfilled";
    private static final String CANCELLED_EVENT = "workorder.cancelled";
    private static final String REOPENED_EVENT = "workorder.reopened";
    private static final String EXTERNAL_DETAILS_UPDATED_EVENT = "workorder.external-details-updated";

    private final DSLContext dsl;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    JooqWorkOrderCommandService(DSLContext dsl, OutboxAppender outbox, ObjectMapper objectMapper) {
        this(dsl, outbox, objectMapper, Clock.systemUTC());
    }

    JooqWorkOrderCommandService(
            DSLContext dsl,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = ExternalWorkOrderConflictException.class)
    public WorkOrderReceipt receive(ReceiveExternalWorkOrderCommand command) {
        UUID id = UUID.randomUUID();
        Instant receivedAt = clock.instant();
        WoWorkOrder wo = WO_WORK_ORDER;
        // 以 (tenant_id, client_code, external_order_code) 唯一约束实现业务幂等：
        // 插入成功即首次接收；冲突（影响行数 0）再回读既有工单判定重放或失败关闭。
        int inserted = dsl.insertInto(wo)
                .set(wo.ID, id)
                .set(wo.TENANT_ID, command.tenantId())
                .set(wo.PROJECT_ID, command.projectId())
                .set(wo.CLIENT_CODE, command.clientCode())
                .set(wo.BRAND_CODE, command.brandCode())
                .set(wo.SERVICE_PRODUCT_CODE, command.serviceProductCode())
                .set(wo.EXTERNAL_ORDER_CODE, command.externalOrderCode())
                .set(wo.PAYLOAD_DIGEST, command.payloadDigest())
                .set(wo.STATUS, "RECEIVED")
                .set(wo.CONFIGURATION_BUNDLE_ID, command.configurationBundleId())
                .set(wo.CONFIGURATION_BUNDLE_CODE, command.configurationBundleCode())
                .set(wo.CONFIGURATION_BUNDLE_VERSION, command.configurationBundleVersion())
                .set(wo.CONFIGURATION_BUNDLE_DIGEST, command.configurationBundleDigest())
                .set(wo.PROVINCE_CODE, command.provinceCode())
                .set(wo.CITY_CODE, command.cityCode())
                .set(wo.DISTRICT_CODE, command.districtCode())
                .set(wo.CUSTOMER_NAME, command.customerName())
                .set(wo.CUSTOMER_MOBILE, command.customerMobile())
                .set(wo.SERVICE_ADDRESS, command.serviceAddress())
                .set(wo.VEHICLE_VIN, command.vehicleVin())
                .set(wo.EXTERNAL_DISPATCHED_AT, command.externalDispatchedAt())
                .set(wo.RECEIVED_AT, receivedAt)
                .set(wo.VERSION, 1L)
                .onConflict(wo.TENANT_ID, wo.CLIENT_CODE, wo.EXTERNAL_ORDER_CODE)
                .doNothing()
                .execute();

        if (inserted == 1) {
            appendReceivedEvent(id, command, receivedAt);
            return receipt(id, command, false, receivedAt);
        }

        Existing existing = findExisting(command.tenantId(), command.clientCode(), command.externalOrderCode());
        if (!existing.payloadDigest().equals(command.payloadDigest())) {
            throw new ExternalWorkOrderConflictException(
                    "external order already exists with a different payload");
        }
        if (!existing.projectId().equals(command.projectId())
                || !existing.bundleId().equals(command.configurationBundleId())
                || !existing.bundleCode().equals(command.configurationBundleCode())
                || !existing.bundleVersion().equals(command.configurationBundleVersion())
                || !existing.bundleDigest().equals(command.configurationBundleDigest())) {
            throw new ExternalWorkOrderConflictException(
                    "external order replay attempted with a different project or configuration bundle");
        }
        return new WorkOrderReceipt(existing.id(), existing.tenantId(), existing.projectId(),
                command.externalOrderCode(), existing.status(), existing.bundleId(),
                existing.bundleCode(), existing.bundleVersion(), existing.bundleDigest(),
                true, existing.receivedAt());
    }

    @Override
    @Transactional
    public WorkOrderActivationReceipt activate(ActivateWorkOrderCommand command) {
        Instant activatedAt = clock.instant();
        WoWorkOrder wo = WO_WORK_ORDER;
        // 条件更新：仅允许 RECEIVED -> ACTIVE，影响行数 0 说明已迁移或状态非法，回读判定。
        int updated = dsl.update(wo)
                .set(wo.STATUS, "ACTIVE")
                .set(wo.ACTIVATED_AT, activatedAt)
                .set(wo.VERSION, wo.VERSION.plus(1))
                .where(wo.TENANT_ID.eq(command.tenantId()))
                .and(wo.ID.eq(command.workOrderId()))
                .and(wo.STATUS.eq("RECEIVED"))
                .execute();

        ActivationRow row = findActivation(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if (!"ACTIVE".equals(row.status())) {
                throw new ExternalWorkOrderConflictException(
                        "work order cannot be activated from status " + row.status());
            }
            return new WorkOrderActivationReceipt(
                    row.workOrderId(), row.status(), row.version(), true, row.activatedAt());
        }

        // 载荷 activatedAt 与信封 occurredAt 必须同源 Instant（写入前 clock）。
        // 禁止把持久化回读的 row.activatedAt() 写入载荷：时间精度往返可能产生纳秒级偏差，
        // 导致时间线消费者拒绝「发生时间与载荷不一致」。回执仍返回持久化时间，保证幂等重放一致。
        WorkOrderActivatedPayload payload = new WorkOrderActivatedPayload(
                row.workOrderId(), activatedAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), ACTIVATED_EVENT,
                command.correlationId(), command.triggerEventId().toString(), payload, activatedAt);
        return new WorkOrderActivationReceipt(
                row.workOrderId(), row.status(), row.version(), false, row.activatedAt());
    }

    @Override
    @Transactional
    public WorkOrderFulfillmentReceipt fulfill(FulfillWorkOrderCommand command) {
        Instant fulfilledAt = clock.instant();
        WoWorkOrder wo = WO_WORK_ORDER;
        // 条件更新：仅允许 ACTIVE -> FULFILLED，影响行数 0 说明已迁移或状态非法，回读判定。
        int updated = dsl.update(wo)
                .set(wo.STATUS, "FULFILLED")
                .set(wo.FULFILLED_AT, fulfilledAt)
                .set(wo.VERSION, wo.VERSION.plus(1))
                .where(wo.TENANT_ID.eq(command.tenantId()))
                .and(wo.ID.eq(command.workOrderId()))
                .and(wo.STATUS.eq("ACTIVE"))
                .execute();

        FulfillmentRow row = findFulfillment(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if (!"FULFILLED".equals(row.status())) {
                throw new ExternalWorkOrderConflictException(
                        "work order cannot be fulfilled from status " + row.status());
            }
            return new WorkOrderFulfillmentReceipt(
                    row.workOrderId(), row.status(), row.version(), true, row.fulfilledAt());
        }

        // 与 activate 相同：载荷与信封共用写入前 Instant；回执用持久化时间保证幂等一致。
        WorkOrderFulfilledPayload payload = new WorkOrderFulfilledPayload(
                row.workOrderId(), command.workflowInstanceId(),
                command.completedStageCodes(), fulfilledAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), FULFILLED_EVENT,
                command.correlationId(), command.triggerEventId().toString(), payload, fulfilledAt);
        return new WorkOrderFulfillmentReceipt(
                row.workOrderId(), row.status(), row.version(), false, row.fulfilledAt());
    }

    @Override
    @Transactional
    public WorkOrderUpdateReceipt updateExternalDetails(UpdateExternalWorkOrderCommand command) {
        Instant updatedAt = clock.instant();
        WoWorkOrder wo = WO_WORK_ORDER;
        UpdateRow current = findUpdateRow(command.tenantId(), command.workOrderId());
        if (command.updateDigest().equals(current.lastExternalUpdateDigest())) {
            return new WorkOrderUpdateReceipt(
                    current.workOrderId(), current.status(), current.version(), true, updatedAt);
        }
        // 乐观并发：状态必须在可更新集合内且 version 匹配期望值，影响行数 0 即并发或状态冲突。
        int updated = dsl.update(wo)
                .set(wo.CUSTOMER_NAME, command.customerName())
                .set(wo.CUSTOMER_MOBILE, command.customerMobile())
                .set(wo.SERVICE_ADDRESS, command.serviceAddress())
                .set(wo.PROVINCE_CODE, command.provinceCode())
                .set(wo.CITY_CODE, command.cityCode())
                .set(wo.DISTRICT_CODE, command.districtCode())
                .set(wo.LAST_EXTERNAL_UPDATE_DIGEST, command.updateDigest())
                .set(wo.VERSION, wo.VERSION.plus(1))
                .where(wo.TENANT_ID.eq(command.tenantId()))
                .and(wo.ID.eq(command.workOrderId()))
                .and(wo.STATUS.in("RECEIVED", "ACTIVE"))
                .and(wo.VERSION.eq(command.expectedVersion()))
                .execute();
        UpdateRow row = findUpdateRow(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if (command.updateDigest().equals(row.lastExternalUpdateDigest())) {
                return new WorkOrderUpdateReceipt(
                        row.workOrderId(), row.status(), row.version(), true, updatedAt);
            }
            throw new ExternalWorkOrderConflictException(
                    "work order cannot be updated from status " + row.status()
                            + " at version " + row.version());
        }
        WorkOrderExternalDetailsUpdatedPayload payload = new WorkOrderExternalDetailsUpdatedPayload(
                row.workOrderId(), command.updateDigest(), command.provinceCode(),
                command.cityCode(), command.districtCode(), updatedAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), EXTERNAL_DETAILS_UPDATED_EVENT,
                command.correlationId(), command.causationId(), payload, updatedAt);
        return new WorkOrderUpdateReceipt(
                row.workOrderId(), row.status(), row.version(), false, updatedAt);
    }

    @Override
    @Transactional
    public WorkOrderCancellationReceipt cancel(CancelWorkOrderCommand command) {
        Instant cancelledAt = clock.instant();
        WoWorkOrder wo = WO_WORK_ORDER;
        // 乐观并发：仅允许 RECEIVED/ACTIVE -> CANCELLED 且 version 匹配，影响行数 0 回读判定重放或冲突。
        int updated = dsl.update(wo)
                .set(wo.STATUS, "CANCELLED")
                .set(wo.CANCELLED_AT, cancelledAt)
                .set(wo.CANCEL_REASON_CODE, command.reasonCode())
                .set(wo.CANCEL_APPROVAL_REF, command.approvalRef())
                .set(wo.VERSION, wo.VERSION.plus(1))
                .where(wo.TENANT_ID.eq(command.tenantId()))
                .and(wo.ID.eq(command.workOrderId()))
                .and(wo.STATUS.in("RECEIVED", "ACTIVE"))
                .and(wo.VERSION.eq(command.expectedVersion()))
                .execute();

        CancellationRow row = findCancellation(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if ("CANCELLED".equals(row.status())
                    && command.reasonCode().equals(row.cancelReasonCode())
                    && java.util.Objects.equals(command.approvalRef(), row.cancelApprovalRef())) {
                return new WorkOrderCancellationReceipt(
                        row.workOrderId(), row.status(), row.version(), true, row.cancelledAt());
            }
            throw new ExternalWorkOrderConflictException(
                    "work order cannot be cancelled from status " + row.status()
                            + " at version " + row.version());
        }

        WorkOrderCancelledPayload payload = new WorkOrderCancelledPayload(
                row.workOrderId(), command.reasonCode(), command.approvalRef(), cancelledAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), CANCELLED_EVENT,
                command.correlationId(), command.causationId(), payload, cancelledAt);
        return new WorkOrderCancellationReceipt(
                row.workOrderId(), row.status(), row.version(), false, row.cancelledAt());
    }

    @Override
    @Transactional
    public WorkOrderReopenReceipt reopen(ReopenWorkOrderCommand command) {
        Instant reopenedAt = clock.instant();
        WoWorkOrder wo = WO_WORK_ORDER;
        // 乐观并发：仅允许 CANCELLED -> ACTIVE 且 version 匹配；activated_at 缺失时以重开时间补齐。
        int updated = dsl.update(wo)
                .set(wo.STATUS, "ACTIVE")
                .set(wo.REOPENED_AT, reopenedAt)
                .set(wo.REOPEN_APPROVAL_REF, command.approvalRef())
                .set(wo.ACTIVATED_AT, DSL.coalesce(wo.ACTIVATED_AT, reopenedAt))
                .set(wo.VERSION, wo.VERSION.plus(1))
                .where(wo.TENANT_ID.eq(command.tenantId()))
                .and(wo.ID.eq(command.workOrderId()))
                .and(wo.STATUS.eq("CANCELLED"))
                .and(wo.VERSION.eq(command.expectedVersion()))
                .execute();

        ReopenRow row = findReopen(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if ("ACTIVE".equals(row.status())
                    && command.approvalRef().equals(row.reopenApprovalRef())) {
                return new WorkOrderReopenReceipt(
                        row.workOrderId(), row.status(), row.version(), true, row.reopenedAt());
            }
            throw new ExternalWorkOrderConflictException(
                    "work order cannot be reopened from status " + row.status()
                            + " at version " + row.version());
        }

        WorkOrderReopenedPayload payload = new WorkOrderReopenedPayload(
                row.workOrderId(),
                row.projectId(),
                new WorkOrderReceivedPayload.ConfigurationBundleRef(
                        row.bundleId(), row.bundleCode(), row.bundleVersion(), row.bundleDigest()),
                command.approvalRef(),
                reopenedAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), REOPENED_EVENT,
                command.correlationId(), command.causationId(), payload, reopenedAt);
        return new WorkOrderReopenReceipt(
                row.workOrderId(), row.status(), row.version(), false, row.reopenedAt());
    }

    private void appendReceivedEvent(
            UUID workOrderId,
            ReceiveExternalWorkOrderCommand command,
            Instant receivedAt
    ) {
        WorkOrderReceivedPayload payload = new WorkOrderReceivedPayload(
                workOrderId,
                command.projectId(),
                command.externalOrderCode(),
                command.clientCode(),
                command.brandCode(),
                command.serviceProductCode(),
                new WorkOrderReceivedPayload.ConfigurationBundleRef(
                        command.configurationBundleId(),
                        command.configurationBundleCode(),
                        command.configurationBundleVersion(),
                        command.configurationBundleDigest()),
                receivedAt);
        appendEvent(
                command.tenantId(), workOrderId, 1, RECEIVED_EVENT,
                command.correlationId(), command.causationId(), payload, receivedAt);
    }

    private void appendEvent(
            String tenantId,
            UUID workOrderId,
            long aggregateVersion,
            String eventType,
            String correlationId,
            String causationId,
            Object payload,
            Instant occurredAt
    ) {
        String payloadJson = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "workorder", eventType, 1,
                "WorkOrder", workOrderId.toString(), aggregateVersion, tenantId,
                correlationId, causationId, workOrderId.toString(), payloadJson,
                Sha256.digest(payloadJson), occurredAt));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("WorkOrder event serialization failed", exception);
        }
    }

    private Existing findExisting(String tenantId, String clientCode, String externalOrderCode) {
        WoWorkOrder wo = WO_WORK_ORDER;
        // fetchSingle 空结果抛 NoDataFoundException：唯一约束冲突后必能回读到行，读不到即数据损坏。
        Record row = dsl.select(wo.ID, wo.TENANT_ID, wo.PROJECT_ID, wo.PAYLOAD_DIGEST, wo.STATUS,
                        wo.CONFIGURATION_BUNDLE_ID, wo.CONFIGURATION_BUNDLE_CODE,
                        wo.CONFIGURATION_BUNDLE_VERSION, wo.CONFIGURATION_BUNDLE_DIGEST, wo.RECEIVED_AT)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.CLIENT_CODE.eq(clientCode))
                .and(wo.EXTERNAL_ORDER_CODE.eq(externalOrderCode))
                .fetchSingle();
        return new Existing(
                row.get(wo.ID),
                row.get(wo.TENANT_ID),
                row.get(wo.PROJECT_ID),
                row.get(wo.PAYLOAD_DIGEST),
                row.get(wo.STATUS),
                row.get(wo.CONFIGURATION_BUNDLE_ID),
                row.get(wo.CONFIGURATION_BUNDLE_CODE),
                row.get(wo.CONFIGURATION_BUNDLE_VERSION),
                row.get(wo.CONFIGURATION_BUNDLE_DIGEST),
                row.get(wo.RECEIVED_AT));
    }

    private ActivationRow findActivation(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        Record row = dsl.select(wo.ID, wo.STATUS, wo.VERSION, wo.ACTIVATED_AT)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchSingle();
        return new ActivationRow(
                row.get(wo.ID),
                row.get(wo.STATUS),
                row.get(wo.VERSION),
                row.get(wo.ACTIVATED_AT));
    }

    private FulfillmentRow findFulfillment(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        Record row = dsl.select(wo.ID, wo.STATUS, wo.VERSION, wo.FULFILLED_AT)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchSingle();
        return new FulfillmentRow(
                row.get(wo.ID),
                row.get(wo.STATUS),
                row.get(wo.VERSION),
                row.get(wo.FULFILLED_AT));
    }

    private CancellationRow findCancellation(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        Record row = dsl.select(wo.ID, wo.STATUS, wo.VERSION, wo.CANCELLED_AT,
                        wo.CANCEL_REASON_CODE, wo.CANCEL_APPROVAL_REF)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchSingle();
        return new CancellationRow(
                row.get(wo.ID),
                row.get(wo.STATUS),
                row.get(wo.VERSION),
                row.get(wo.CANCELLED_AT),
                row.get(wo.CANCEL_REASON_CODE),
                row.get(wo.CANCEL_APPROVAL_REF));
    }

    private UpdateRow findUpdateRow(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        Record row = dsl.select(wo.ID, wo.STATUS, wo.VERSION, wo.LAST_EXTERNAL_UPDATE_DIGEST)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchSingle();
        return new UpdateRow(
                row.get(wo.ID),
                row.get(wo.STATUS),
                row.get(wo.VERSION),
                row.get(wo.LAST_EXTERNAL_UPDATE_DIGEST));
    }

    private ReopenRow findReopen(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        Record row = dsl.select(wo.ID, wo.PROJECT_ID, wo.STATUS, wo.VERSION, wo.REOPENED_AT,
                        wo.REOPEN_APPROVAL_REF, wo.CONFIGURATION_BUNDLE_ID, wo.CONFIGURATION_BUNDLE_CODE,
                        wo.CONFIGURATION_BUNDLE_VERSION, wo.CONFIGURATION_BUNDLE_DIGEST)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchSingle();
        return new ReopenRow(
                row.get(wo.ID),
                row.get(wo.PROJECT_ID),
                row.get(wo.STATUS),
                row.get(wo.VERSION),
                row.get(wo.REOPENED_AT),
                row.get(wo.REOPEN_APPROVAL_REF),
                row.get(wo.CONFIGURATION_BUNDLE_ID),
                row.get(wo.CONFIGURATION_BUNDLE_CODE),
                row.get(wo.CONFIGURATION_BUNDLE_VERSION),
                row.get(wo.CONFIGURATION_BUNDLE_DIGEST));
    }

    private static WorkOrderReceipt receipt(
            UUID id,
            ReceiveExternalWorkOrderCommand command,
            boolean replay,
            Instant receivedAt
    ) {
        return new WorkOrderReceipt(id, command.tenantId(), command.projectId(),
                command.externalOrderCode(), "RECEIVED", command.configurationBundleId(),
                command.configurationBundleCode(), command.configurationBundleVersion(),
                command.configurationBundleDigest(), replay, receivedAt);
    }

    private record Existing(
            UUID id,
            String tenantId,
            UUID projectId,
            String payloadDigest,
            String status,
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            String bundleDigest,
            Instant receivedAt
    ) {
    }

    private record ActivationRow(
            UUID workOrderId,
            String status,
            long version,
            Instant activatedAt
    ) {
    }

    private record FulfillmentRow(
            UUID workOrderId,
            String status,
            long version,
            Instant fulfilledAt
    ) {
    }

    private record CancellationRow(
            UUID workOrderId,
            String status,
            long version,
            Instant cancelledAt,
            String cancelReasonCode,
            String cancelApprovalRef
    ) {
    }

    private record UpdateRow(
            UUID workOrderId,
            String status,
            long version,
            String lastExternalUpdateDigest
    ) {
    }

    private record ReopenRow(
            UUID workOrderId,
            UUID projectId,
            String status,
            long version,
            Instant reopenedAt,
            String reopenApprovalRef,
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            String bundleDigest
    ) {
    }
}
