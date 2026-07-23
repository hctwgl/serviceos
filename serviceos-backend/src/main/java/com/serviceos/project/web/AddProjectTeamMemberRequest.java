package com.serviceos.project.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

record AddProjectTeamMemberRequest(@NotNull UUID principalId) {
}
