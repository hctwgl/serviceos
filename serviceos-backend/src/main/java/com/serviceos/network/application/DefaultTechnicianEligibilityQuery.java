package com.serviceos.network.application;

import com.serviceos.identity.api.PrincipalStatusQuery;
import com.serviceos.network.api.TechnicianEligibilityQuery;
import com.serviceos.network.domain.ServiceNetwork;
import com.serviceos.network.domain.TechnicianProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * M185-04 可接单判定：Principal ACTIVE、师傅档案 ACTIVE、网点 ACTIVE、有效服务关系与至少一条 APPROVED 资质。
 */
@Service
final class DefaultTechnicianEligibilityQuery implements TechnicianEligibilityQuery {
    private final NetworkDirectoryRepository directory;
    private final PrincipalStatusQuery principalStatus;
    private final ThreadLocal<String> lastFailureReason = new ThreadLocal<>();

    DefaultTechnicianEligibilityQuery(NetworkDirectoryRepository directory, PrincipalStatusQuery principalStatus) {
        this.directory = directory;
        this.principalStatus = principalStatus;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAcceptAssignment(
            String tenantId, UUID technicianPrincipalId, UUID serviceNetworkId, Instant at
    ) {
        lastFailureReason.remove();
        if (!principalStatus.isActive(tenantId, technicianPrincipalId)) {
            lastFailureReason.set("Principal 未激活");
            return false;
        }
        TechnicianProfile profile = directory.findTechnicianProfileByPrincipal(tenantId, technicianPrincipalId)
                .orElse(null);
        if (profile == null || profile.status() != TechnicianProfile.Status.ACTIVE) {
            lastFailureReason.set("师傅档案不存在或已停用");
            return false;
        }
        ServiceNetwork network = directory.findNetwork(tenantId, serviceNetworkId).orElse(null);
        if (network == null || network.status() != ServiceNetwork.Status.ACTIVE) {
            lastFailureReason.set("网点不存在或已清退");
            return false;
        }
        if (directory.findActiveTechnicianMembership(tenantId, serviceNetworkId, profile.id(), at).isEmpty()) {
            lastFailureReason.set("师傅与网点无有效服务关系");
            return false;
        }
        if (!directory.hasApprovedQualification(tenantId, profile.id(), at)) {
            lastFailureReason.set("缺少有效已批准资质");
            return false;
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public String explainIneligibility(
            String tenantId, UUID technicianPrincipalId, UUID serviceNetworkId, Instant at
    ) {
        canAcceptAssignment(tenantId, technicianPrincipalId, serviceNetworkId, at);
        return lastFailureReason.get();
    }

    String lastFailureReason() {
        return lastFailureReason.get();
    }
}
