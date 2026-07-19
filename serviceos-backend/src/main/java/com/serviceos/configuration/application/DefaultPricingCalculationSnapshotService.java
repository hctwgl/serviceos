package com.serviceos.configuration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PricingCalculationSnapshotCommand;
import com.serviceos.configuration.api.PricingCalculationSnapshotService;
import com.serviceos.configuration.api.PricingResolution;
import com.serviceos.configuration.api.PricingResolveCommand;
import com.serviceos.configuration.api.PricingRuntime;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M327：履约事实 + PricingRuntime 试算 → SHADOW CalculationSnapshot。
 *
 * <p>与 Inbox 同事务；不落账、不创建结算。无 PRICING 资产时 Inbox N/A 完成。</p>
 */
@Service
public class DefaultPricingCalculationSnapshotService implements PricingCalculationSnapshotService {
    private static final String CONSUMER = "configuration.pricing.workorder-fulfilled.v1";
    private static final String SYSTEM_ACTOR = "system:pricing-runtime";

    private final ConfigurationService configurations;
    private final PricingRuntime pricingRuntime;
    private final PricingCalculationSnapshotStore store;
    private final InboxService inbox;
    private final AuditAppender audit;
    private final Clock clock;

    public DefaultPricingCalculationSnapshotService(
            ConfigurationService configurations,
            PricingRuntime pricingRuntime,
            PricingCalculationSnapshotStore store,
            InboxService inbox,
            AuditAppender audit,
            Clock clock
    ) {
        this.configurations = configurations;
        this.pricingRuntime = pricingRuntime;
        this.store = store;
        this.inbox = inbox;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void capture(PricingCalculationSnapshotCommand command) {
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
                ConfigurationAssetType.PRICING);
        if (assets.isEmpty()) {
            inbox.complete(command.tenantId(), CONSUMER, command.eventId(),
                    Sha256.digest(command.workOrderId() + "|NO_PRICING_ASSETS"));
            return;
        }

        Instant asOf = clock.instant();
        store.saveFacts(
                command.tenantId(), command.projectId(), command.workOrderId(),
                command.eventId(), asOf, command.facts());
        String factsDigest = Sha256.digest(command.facts().stream()
                .map(fact -> fact.factCode() + "=" + nullToEmpty(fact.valueText()))
                .collect(Collectors.joining(";")));

        int captured = 0;
        long totalMinor = 0L;
        for (ConfigurationAssetDefinition asset : assets) {
            String pricingKey = asset.assetKey();
            if (store.snapshotExists(command.tenantId(), command.eventId(), pricingKey)) {
                captured++;
                continue;
            }
            PricingResolution resolution = pricingRuntime.resolve(new PricingResolveCommand(
                    command.tenantId(),
                    command.bundleId(),
                    command.bundleDigest(),
                    pricingKey,
                    command.expressionContext()));
            store.saveSnapshot(
                    command.tenantId(),
                    command.projectId(),
                    command.workOrderId(),
                    command.eventId(),
                    command.sourceEventType(),
                    command.bundleId(),
                    command.bundleDigest(),
                    factsDigest,
                    command.correlationId(),
                    asOf,
                    resolution);
            captured++;
            totalMinor = Math.addExact(totalMinor, resolution.totalAmountMinor());
        }

        audit.append(new AuditEntry(
                UUID.randomUUID(), command.tenantId(), SYSTEM_ACTOR,
                "PRICING_CALCULATION_SNAPSHOT_CAPTURED", "pricing.snapshot", "WorkOrder",
                command.workOrderId().toString(),
                "ALLOW", List.of(), "pricing-runtime-v1", "SHADOW", null,
                Sha256.digest(captured + "|" + totalMinor + "|" + factsDigest),
                command.correlationId(), asOf));
        inbox.complete(command.tenantId(), CONSUMER, command.eventId(),
                Sha256.digest(command.workOrderId() + "|SHADOW|" + captured + "|" + totalMinor));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
