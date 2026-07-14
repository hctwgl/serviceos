package com.serviceos.task.application;

import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.CancelHandlingTaskCommand;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.HandlingTaskCancellationReceipt;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.CreateWorkflowTaskCommand;

public interface TaskSchedulingStore {
    ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command);

    ScheduledTaskView createHandlingTask(CreateHandlingTaskCommand command);

    HandlingTaskCancellationReceipt cancelHandlingTask(CancelHandlingTaskCommand command);

    ScheduledTaskView createWorkflowTask(CreateWorkflowTaskCommand command);
}
