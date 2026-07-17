package com.serviceos.network.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.time.Instant;
import java.util.UUID;

/**
 * M204 Network Portal 本网点师傅关系绑定/终止与资质 PENDING 提交。
 * <p>
 * networkId 仅来自可信头 X-Network-Context；禁止客户端自报。
 */
public interface NetworkPortalManageTechnicianService {
    NetworkTechnicianMembershipView createMembership(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID technicianProfileId,
            Instant validFrom);

    NetworkTechnicianMembershipView terminateMembership(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID membershipId,
            long expectedVersion,
            String reason);

    TechnicianQualificationView submitQualification(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID technicianProfileId,
            String qualificationCode,
            Instant validFrom,
            Instant validTo);
}
