package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/** Network Portal 师傅列表项。 */
public record NetworkPortalTechnicianItem(
        UUID membershipId,
        UUID technicianProfileId,
        UUID principalId,
        String displayName,
        String profileStatus,
        String membershipStatus,
        Instant validFrom,
        Instant validTo,
        long membershipVersion
) {
}
