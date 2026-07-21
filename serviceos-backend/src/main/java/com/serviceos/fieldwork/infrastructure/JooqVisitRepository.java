package com.serviceos.fieldwork.infrastructure;

import com.serviceos.fieldwork.api.VisitCommandReceipt;
import com.serviceos.fieldwork.api.VisitLocation;
import com.serviceos.fieldwork.application.GeofencePolicy;
import com.serviceos.fieldwork.application.VisitAggregate;
import com.serviceos.fieldwork.application.VisitRepository;
import com.serviceos.jooq.generated.tables.FldVisit;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.FldGeofencePolicy.FLD_GEOFENCE_POLICY;
import static com.serviceos.jooq.generated.tables.FldVisit.FLD_VISIT;
import static com.serviceos.jooq.generated.tables.FldVisitCommandResult.FLD_VISIT_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.FldVisitFact.FLD_VISIT_FACT;
import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;

/**
 * Visit 聚合的 jOOQ 持久化（ADR-091）。序号分配先 FOR UPDATE 锁定 Task 权威行再 MAX+1；
 * 终止迁移携带版本与 IN_PROGRESS 条件并校验影响行数；jsonb 引用列表直接绑定 JSON 文本。
 */
@Repository
final class JooqVisitRepository implements VisitRepository {
    private static final FldVisit V = FLD_VISIT;
    private static final List<SelectField<?>> VISIT_FIELDS = List.of(
            V.VISIT_ID, V.TENANT_ID, V.PROJECT_ID, V.WORK_ORDER_ID, V.TASK_ID, V.APPOINTMENT_ID,
            V.VISIT_SEQUENCE, V.TECHNICIAN_ID, V.NETWORK_ID, V.STATUS,
            V.CHECK_IN_CAPTURED_AT, V.CHECK_IN_RECEIVED_AT, V.CHECK_IN_LATITUDE,
            V.CHECK_IN_LONGITUDE, V.CHECK_IN_ACCURACY_METERS, V.GEOFENCE_RESULT,
            V.GEOFENCE_DISTANCE_METERS, V.GEOFENCE_POLICY_VERSION, V.POLICY_DECISION,
            V.DEVICE_ID, V.DEVICE_COMMAND_ID, V.OFFLINE_FLAG,
            V.CHECK_OUT_CAPTURED_AT, V.CHECK_OUT_RECEIVED_AT, V.RESULT_CODE, V.EXCEPTION_CODE,
            V.NOTE, V.OPERATION_REFS, V.EVIDENCE_REFS,
            V.AGGREGATE_VERSION, V.CREATED_BY, V.CREATED_AT, V.UPDATED_AT);

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqVisitRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<VisitAggregate> findById(String tenantId, UUID visitId) {
        return dsl.select(VISIT_FIELDS)
                .from(V)
                .where(V.TENANT_ID.eq(tenantId))
                .and(V.VISIT_ID.eq(visitId))
                .fetchOptional()
                .map(this::visit);
    }

    @Override
    public List<VisitAggregate> findByWorkOrder(String tenantId, UUID workOrderId) {
        return dsl.select(VISIT_FIELDS)
                .from(V)
                .where(V.TENANT_ID.eq(tenantId))
                .and(V.WORK_ORDER_ID.eq(workOrderId))
                .orderBy(V.CHECK_IN_CAPTURED_AT, V.VISIT_ID)
                .fetch(this::visit);
    }

    @Override
    public Optional<GeofencePolicy> findGeofencePolicy(String tenantId, UUID projectId) {
        return dsl.select(
                        FLD_GEOFENCE_POLICY.PROJECT_ID, FLD_GEOFENCE_POLICY.TARGET_LATITUDE,
                        FLD_GEOFENCE_POLICY.TARGET_LONGITUDE, FLD_GEOFENCE_POLICY.RADIUS_METERS,
                        FLD_GEOFENCE_POLICY.MAX_ACCURACY_METERS, FLD_GEOFENCE_POLICY.EXCEPTION_ACTION,
                        FLD_GEOFENCE_POLICY.POLICY_VERSION)
                .from(FLD_GEOFENCE_POLICY)
                .where(FLD_GEOFENCE_POLICY.TENANT_ID.eq(tenantId))
                .and(FLD_GEOFENCE_POLICY.PROJECT_ID.eq(projectId))
                .fetchOptional(row -> new GeofencePolicy(
                        row.get(FLD_GEOFENCE_POLICY.PROJECT_ID),
                        row.get(FLD_GEOFENCE_POLICY.TARGET_LATITUDE).doubleValue(),
                        row.get(FLD_GEOFENCE_POLICY.TARGET_LONGITUDE).doubleValue(),
                        row.get(FLD_GEOFENCE_POLICY.RADIUS_METERS).doubleValue(),
                        row.get(FLD_GEOFENCE_POLICY.MAX_ACCURACY_METERS).doubleValue(),
                        row.get(FLD_GEOFENCE_POLICY.EXCEPTION_ACTION),
                        row.get(FLD_GEOFENCE_POLICY.POLICY_VERSION)));
    }

    @Override
    public int nextSequence(String tenantId, UUID taskId) {
        // 同一 Task 的并发二次上门先锁定 Task 权威行，避免 MAX+1 竞争产生重复序号。
        Long taskVersion = dsl.select(TSK_TASK.VERSION)
                .from(TSK_TASK)
                .where(TSK_TASK.TENANT_ID.eq(tenantId))
                .and(TSK_TASK.TASK_ID.eq(taskId))
                .forUpdate()
                .fetchOne(TSK_TASK.VERSION);
        if (taskVersion == null) {
            throw new IllegalStateException("Visit Task disappeared while allocating sequence");
        }
        return dsl.select(DSL.coalesce(DSL.max(V.VISIT_SEQUENCE), 0).plus(1))
                .from(V)
                .where(V.TENANT_ID.eq(tenantId))
                .and(V.TASK_ID.eq(taskId))
                .fetchSingle()
                .value1();
    }

    @Override
    public void create(VisitAggregate visit) {
        dsl.insertInto(V)
                .set(V.VISIT_ID, visit.visitId())
                .set(V.TENANT_ID, visit.tenantId())
                .set(V.PROJECT_ID, visit.projectId())
                .set(V.WORK_ORDER_ID, visit.workOrderId())
                .set(V.TASK_ID, visit.taskId())
                .set(V.APPOINTMENT_ID, visit.appointmentId())
                .set(V.VISIT_SEQUENCE, visit.visitSequence())
                .set(V.TECHNICIAN_ID, visit.technicianId())
                .set(V.NETWORK_ID, visit.networkId())
                .set(V.STATUS, visit.status())
                .set(V.CHECK_IN_CAPTURED_AT, visit.checkInCapturedAt())
                .set(V.CHECK_IN_RECEIVED_AT, visit.checkInReceivedAt())
                .set(V.CHECK_IN_LATITUDE, BigDecimal.valueOf(visit.checkInLocation().latitude()))
                .set(V.CHECK_IN_LONGITUDE, BigDecimal.valueOf(visit.checkInLocation().longitude()))
                .set(V.CHECK_IN_ACCURACY_METERS, BigDecimal.valueOf(visit.checkInLocation().accuracyMeters()))
                .set(V.GEOFENCE_RESULT, visit.geofenceResult())
                .set(V.GEOFENCE_DISTANCE_METERS, decimal(visit.geofenceDistanceMeters()))
                .set(V.GEOFENCE_POLICY_VERSION, visit.geofencePolicyVersion())
                .set(V.POLICY_DECISION, visit.policyDecision())
                .set(V.DEVICE_ID, visit.deviceId())
                .set(V.DEVICE_COMMAND_ID, visit.deviceCommandId())
                .set(V.OFFLINE_FLAG, visit.offline())
                .set(V.AGGREGATE_VERSION, visit.aggregateVersion())
                .set(V.CREATED_BY, visit.createdBy())
                .set(V.CREATED_AT, visit.createdAt())
                .set(V.UPDATED_AT, visit.updatedAt())
                .execute();
    }

    @Override
    public boolean terminate(
            String tenantId, UUID visitId, long expectedVersion, String newStatus,
            Instant capturedAt, Instant receivedAt, String resultCode, String exceptionCode,
            String note, List<String> operationRefs, List<String> evidenceRefs
    ) {
        return dsl.update(V)
                .set(V.STATUS, newStatus)
                .set(V.CHECK_OUT_CAPTURED_AT, capturedAt)
                .set(V.CHECK_OUT_RECEIVED_AT, receivedAt)
                .set(V.RESULT_CODE, resultCode)
                .set(V.EXCEPTION_CODE, exceptionCode)
                .set(V.NOTE, note)
                .set(V.OPERATION_REFS, json(operationRefs))
                .set(V.EVIDENCE_REFS, json(evidenceRefs))
                .set(V.AGGREGATE_VERSION, V.AGGREGATE_VERSION.plus(1))
                .set(V.UPDATED_AT, receivedAt)
                .where(V.TENANT_ID.eq(tenantId))
                .and(V.VISIT_ID.eq(visitId))
                .and(V.AGGREGATE_VERSION.eq(expectedVersion))
                .and(V.STATUS.eq("IN_PROGRESS"))
                .execute() == 1;
    }

    @Override
    public void appendFact(
            String tenantId, UUID visitId, long aggregateVersion, String factType,
            Instant capturedAt, Instant receivedAt, Double latitude, Double longitude,
            Double accuracyMeters, String geofenceResult, String resultCode, String exceptionCode,
            String note, List<String> references, String actorId, String deviceId, boolean offline
    ) {
        dsl.insertInto(FLD_VISIT_FACT)
                .set(FLD_VISIT_FACT.VISIT_FACT_ID, UUID.randomUUID())
                .set(FLD_VISIT_FACT.TENANT_ID, tenantId)
                .set(FLD_VISIT_FACT.VISIT_ID, visitId)
                .set(FLD_VISIT_FACT.AGGREGATE_VERSION, aggregateVersion)
                .set(FLD_VISIT_FACT.FACT_TYPE, factType)
                .set(FLD_VISIT_FACT.CAPTURED_AT, capturedAt)
                .set(FLD_VISIT_FACT.RECEIVED_AT, receivedAt)
                .set(FLD_VISIT_FACT.LATITUDE, decimal(latitude))
                .set(FLD_VISIT_FACT.LONGITUDE, decimal(longitude))
                .set(FLD_VISIT_FACT.ACCURACY_METERS, decimal(accuracyMeters))
                .set(FLD_VISIT_FACT.GEOFENCE_RESULT, geofenceResult)
                .set(FLD_VISIT_FACT.RESULT_CODE, resultCode)
                .set(FLD_VISIT_FACT.EXCEPTION_CODE, exceptionCode)
                .set(FLD_VISIT_FACT.NOTE, note)
                .set(FLD_VISIT_FACT.REFERENCE_LIST, json(references))
                .set(FLD_VISIT_FACT.ACTOR_ID, actorId)
                .set(FLD_VISIT_FACT.DEVICE_ID, deviceId)
                .set(FLD_VISIT_FACT.OFFLINE_FLAG, offline)
                .execute();
    }

    @Override
    public void saveResult(String tenantId, String operationType, String idempotencyKey, VisitCommandReceipt receipt) {
        dsl.insertInto(FLD_VISIT_COMMAND_RESULT)
                .set(FLD_VISIT_COMMAND_RESULT.TENANT_ID, tenantId)
                .set(FLD_VISIT_COMMAND_RESULT.OPERATION_TYPE, operationType)
                .set(FLD_VISIT_COMMAND_RESULT.IDEMPOTENCY_KEY, idempotencyKey)
                .set(FLD_VISIT_COMMAND_RESULT.VISIT_ID, receipt.visitId())
                .set(FLD_VISIT_COMMAND_RESULT.STATUS, receipt.status())
                .set(FLD_VISIT_COMMAND_RESULT.AGGREGATE_VERSION, receipt.aggregateVersion())
                .set(FLD_VISIT_COMMAND_RESULT.GEOFENCE_RESULT, receipt.geofenceResult())
                .set(FLD_VISIT_COMMAND_RESULT.POLICY_DECISION, receipt.policyDecision())
                .set(FLD_VISIT_COMMAND_RESULT.OCCURRED_AT, receipt.occurredAt())
                .execute();
    }

    @Override
    public VisitCommandReceipt findResult(String tenantId, String operationType, String idempotencyKey) {
        return dsl.select(
                        FLD_VISIT_COMMAND_RESULT.VISIT_ID,
                        FLD_VISIT_COMMAND_RESULT.STATUS,
                        FLD_VISIT_COMMAND_RESULT.AGGREGATE_VERSION,
                        FLD_VISIT_COMMAND_RESULT.GEOFENCE_RESULT,
                        FLD_VISIT_COMMAND_RESULT.POLICY_DECISION,
                        FLD_VISIT_COMMAND_RESULT.OCCURRED_AT)
                .from(FLD_VISIT_COMMAND_RESULT)
                .where(FLD_VISIT_COMMAND_RESULT.TENANT_ID.eq(tenantId))
                .and(FLD_VISIT_COMMAND_RESULT.OPERATION_TYPE.eq(operationType))
                .and(FLD_VISIT_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(row -> new VisitCommandReceipt(
                        row.get(FLD_VISIT_COMMAND_RESULT.VISIT_ID),
                        row.get(FLD_VISIT_COMMAND_RESULT.STATUS),
                        row.get(FLD_VISIT_COMMAND_RESULT.AGGREGATE_VERSION),
                        row.get(FLD_VISIT_COMMAND_RESULT.GEOFENCE_RESULT),
                        row.get(FLD_VISIT_COMMAND_RESULT.POLICY_DECISION),
                        row.get(FLD_VISIT_COMMAND_RESULT.OCCURRED_AT)))
                .orElseThrow(() -> new IllegalStateException("Frozen Visit result is missing"));
    }

    private VisitAggregate visit(Record row) {
        return new VisitAggregate(
                row.get(V.VISIT_ID), row.get(V.TENANT_ID), row.get(V.PROJECT_ID),
                row.get(V.WORK_ORDER_ID), row.get(V.TASK_ID), row.get(V.APPOINTMENT_ID),
                row.get(V.VISIT_SEQUENCE), row.get(V.TECHNICIAN_ID), row.get(V.NETWORK_ID),
                row.get(V.STATUS), row.get(V.CHECK_IN_CAPTURED_AT), row.get(V.CHECK_IN_RECEIVED_AT),
                new VisitLocation(row.get(V.CHECK_IN_LATITUDE).doubleValue(),
                        row.get(V.CHECK_IN_LONGITUDE).doubleValue(),
                        row.get(V.CHECK_IN_ACCURACY_METERS).doubleValue()),
                row.get(V.GEOFENCE_RESULT), nullableDouble(row.get(V.GEOFENCE_DISTANCE_METERS)),
                row.get(V.GEOFENCE_POLICY_VERSION), row.get(V.POLICY_DECISION), row.get(V.DEVICE_ID),
                row.get(V.DEVICE_COMMAND_ID), Boolean.TRUE.equals(row.get(V.OFFLINE_FLAG)),
                row.get(V.CHECK_OUT_CAPTURED_AT), row.get(V.CHECK_OUT_RECEIVED_AT),
                row.get(V.RESULT_CODE), row.get(V.EXCEPTION_CODE),
                row.get(V.NOTE), strings(row.get(V.OPERATION_REFS)), strings(row.get(V.EVIDENCE_REFS)),
                row.get(V.AGGREGATE_VERSION), row.get(V.CREATED_BY), row.get(V.CREATED_AT),
                row.get(V.UPDATED_AT));
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Visit references serialization failed", exception);
        }
    }

    private List<String> strings(String value) {
        if (value == null) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            ArrayList<String> result = new ArrayList<>();
            node.forEach(item -> result.add(item.asText()));
            return List.copyOf(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored Visit references are invalid", exception);
        }
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static Double nullableDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
