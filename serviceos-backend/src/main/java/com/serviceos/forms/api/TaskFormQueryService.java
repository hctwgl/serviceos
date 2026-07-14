package com.serviceos.forms.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

public interface TaskFormQueryService {
    List<TaskFormDefinition> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId);
}
