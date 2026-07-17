package com.serviceos.identity.api;

import com.serviceos.shared.CommandMetadata;

import java.time.Instant;
import java.util.UUID;

public interface SecurityPrincipalCommandService {
    SecurityPrincipalView linkIdentity(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, long expectedVersion,
            String issuer, String subject, String clientId);

    SecurityPrincipalView disable(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, long expectedVersion, String reason);

    SecurityPrincipalView enable(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, long expectedVersion, String reason);

    SecurityPrincipalView updateProfile(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, long expectedVersion,
            String displayName, String employeeNumber);

    PrincipalPersonaView addPersona(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, long expectedVersion,
            String personaType, Instant validFrom, Instant validTo);
}
