package com.serviceos.appointment.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/**
 * M197 Network Portal 预约协作公开边界。
 * <p>
 * 能力：NETWORK scope {@code networkPortal.manageAppointment}；委托
 * {@link AppointmentService#propose}/{@link AppointmentService#confirm}/{@link AppointmentService#listByTask}。
 * 确认方类型仅允许 {@code NETWORK_MEMBER}/{@code NETWORK}，禁止伪装 {@code TECHNICIAN}。
 */
public interface NetworkPortalAppointmentService {
    List<AppointmentView> listByTask(
            CurrentPrincipal principal,
            String correlationId,
            String networkContextHeader,
            UUID taskId
    );

    AppointmentCommandReceipt propose(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            ProposeAppointmentCommand command
    );

    AppointmentCommandReceipt confirm(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID appointmentId,
            long expectedVersion,
            String confirmedPartyType,
            String confirmedPartyRef,
            String confirmationChannel
    );
}
