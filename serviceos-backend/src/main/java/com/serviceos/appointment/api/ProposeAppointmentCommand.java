package com.serviceos.appointment.api;

import java.util.Objects;
import java.util.UUID;

/** 提议预约；网点和师傅快照由服务端从当前责任事实解析，客户端不能自报。 */
public record ProposeAppointmentCommand(
        UUID taskId,
        AppointmentType type,
        AppointmentWindow window,
        String addressRef,
        String addressVersion
) {
    public ProposeAppointmentCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        type = Objects.requireNonNull(type, "type");
        window = Objects.requireNonNull(window, "window");
        addressRef = required(addressRef, "addressRef", 200);
        addressVersion = required(addressVersion, "addressVersion", 100);
    }

    private static String required(String value, String name, int max) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " must contain at most " + max + " characters");
        }
        return normalized;
    }
}
