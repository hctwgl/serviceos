package com.serviceos.configuration.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** 项目 Bundle 通道激活、流量调整、晋级与回滚。 */
public interface BundleChannelActivationService {
    BundleChannelActivationView activate(
            CurrentPrincipal principal, CommandMetadata metadata, ActivateBundleChannelCommand command);

    BundleChannelActivationView adjustCanaryTraffic(
            CurrentPrincipal principal, CommandMetadata metadata, AdjustCanaryTrafficCommand command);

    BundleChannelActivationView promoteCanary(
            CurrentPrincipal principal, CommandMetadata metadata, UUID canaryActivationId, String approvalRef);

    BundleChannelActivationView rollbackStable(
            CurrentPrincipal principal, CommandMetadata metadata, UUID stableActivationId, String approvalRef);

    List<BundleChannelActivationView> list(
            CurrentPrincipal principal, String correlationId, UUID projectId);
}
