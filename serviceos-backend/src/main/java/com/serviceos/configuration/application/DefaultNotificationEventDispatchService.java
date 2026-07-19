package com.serviceos.configuration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.RolePrincipalDirectoryQuery;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.NotificationEventDispatchCommand;
import com.serviceos.configuration.api.NotificationEventDispatchService;
import com.serviceos.configuration.api.NotificationResolution;
import com.serviceos.configuration.api.NotificationResolveCommand;
import com.serviceos.configuration.api.NotificationRuntime;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M326：事件驱动 NOTIFICATION 派发与 Intent/Delivery/Attempt 持久化。
 *
 * <p>通道发送目前在同事务内（LocalReference 无网络 I/O）。真实短信/邮件供应商接入时，
 * 必须把网络调用移出持锁事务，仅保留 Attempt 技术记录与 Task 重试时钟。</p>
 */
@Service
public class DefaultNotificationEventDispatchService implements NotificationEventDispatchService {
    private static final String CONSUMER = "configuration.notification.task-event.v1";
    private static final String SYSTEM_ACTOR = "system:notification-runtime";

    private final ConfigurationService configurations;
    private final NotificationRuntime notificationRuntime;
    private final RolePrincipalDirectoryQuery rolePrincipals;
    private final NotificationDispatchStore dispatchStore;
    private final InboxService inbox;
    private final AuditAppender audit;
    private final Clock clock;

    public DefaultNotificationEventDispatchService(
            ConfigurationService configurations,
            NotificationRuntime notificationRuntime,
            RolePrincipalDirectoryQuery rolePrincipals,
            NotificationDispatchStore dispatchStore,
            InboxService inbox,
            AuditAppender audit,
            Clock clock
    ) {
        this.configurations = configurations;
        this.notificationRuntime = notificationRuntime;
        this.rolePrincipals = rolePrincipals;
        this.dispatchStore = dispatchStore;
        this.inbox = inbox;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void dispatch(NotificationEventDispatchCommand command) {
        InboxDecision decision = inbox.begin(
                command.tenantId(), CONSUMER, command.eventId(),
                command.schemaVersion(), command.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                command.tenantId(),
                command.bundleId(),
                command.bundleDigest(),
                ConfigurationAssetType.NOTIFICATION);
        if (assets.isEmpty()) {
            inbox.complete(command.tenantId(), CONSUMER, command.eventId(),
                    Sha256.digest(command.taskId() + "|NO_NOTIFICATION_ASSETS"));
            return;
        }

        Instant asOf = clock.instant();
        Map<String, List<String>> recipientsByRole = rolePrincipals.listActivePrincipalsGroupedByRoleCode(
                command.tenantId(), command.projectId(), asOf);

        int dispatched = 0;
        int manual = 0;
        for (ConfigurationAssetDefinition asset : assets) {
            String policyKey = asset.assetKey();
            if (dispatchStore.intentExists(command.tenantId(), command.eventId(), policyKey)) {
                dispatched++;
                continue;
            }
            NotificationResolution resolution = notificationRuntime.resolveAndDispatch(
                    new NotificationResolveCommand(
                            command.tenantId(),
                            command.bundleId(),
                            command.bundleDigest(),
                            policyKey,
                            command.sourceEventType(),
                            command.eventId().toString(),
                            command.expressionContext(),
                            recipientsByRole,
                            Map.of(
                                    "taskId", command.taskId().toString(),
                                    "eventType", command.sourceEventType())));
            if (resolution.attempts().isEmpty() && !resolution.requiresManualIntervention()) {
                continue;
            }
            dispatchStore.saveResolution(
                    command.tenantId(),
                    command.projectId(),
                    command.eventId(),
                    command.sourceEventType(),
                    command.sourceAggregateType(),
                    command.sourceAggregateId(),
                    command.workOrderId(),
                    command.taskId(),
                    command.bundleId(),
                    command.bundleDigest(),
                    command.correlationId(),
                    asOf,
                    resolution);
            dispatched++;
            if (resolution.requiresManualIntervention()) {
                manual++;
            }
        }

        String resultCode = manual > 0 ? "PARTIAL_MANUAL" : (dispatched > 0 ? "DISPATCHED" : "NO_MATCH");
        audit.append(new AuditEntry(
                UUID.randomUUID(), command.tenantId(), SYSTEM_ACTOR,
                "NOTIFICATION_RUNTIME_DISPATCHED", "notification.dispatch", "Task",
                command.taskId().toString(),
                "ALLOW", List.of(), "notification-runtime-v1", resultCode, null,
                Sha256.digest(command.sourceEventType() + "|" + dispatched + "|" + manual),
                command.correlationId(), asOf));
        inbox.complete(command.tenantId(), CONSUMER, command.eventId(),
                Sha256.digest(command.taskId() + "|" + resultCode + "|" + dispatched + "|" + manual));
    }
}
