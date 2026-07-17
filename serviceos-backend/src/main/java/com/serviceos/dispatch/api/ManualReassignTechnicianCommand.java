package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/** Admin/Portal 同网点师傅改派命令：强制携带 networkAssigneeId 与 reasonCode。 */
public record ManualReassignTechnicianCommand(
        UUID taskId,
        String networkAssigneeId,
        String technicianAssigneeId,
        String businessType,
        String reasonCode
) {
    public ManualReassignTechnicianCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        networkAssigneeId = text(networkAssigneeId, "networkAssigneeId", 128);
        technicianAssigneeId = text(technicianAssigneeId, "technicianAssigneeId", 128);
        businessType = text(businessType, "businessType", 100);
        reasonCode = reason(reasonCode);
    }

    private static String text(String value, String name, int max) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }

    private static String reason(String value) {
        String normalized = Objects.requireNonNull(value, "reasonCode").trim();
        if (!normalized.matches("^[A-Z][A-Z0-9_]{1,99}$")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
        return normalized;
    }
}
