package com.serviceos.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

record AssignProjectRegionPersonnelRequest(
        @NotBlank @Size(max = 32) String regionCode,
        @NotBlank @Size(max = 40) String positionCode,
        @NotNull UUID principalId,
        UUID expectedCurrentAssignmentId,
        boolean allowInheritance,
        @NotBlank @Size(max = 500) String reason
) {
}
