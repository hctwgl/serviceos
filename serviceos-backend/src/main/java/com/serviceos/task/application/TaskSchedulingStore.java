package com.serviceos.task.application;

import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.CreateWorkflowTaskCommand;

public interface TaskSchedulingStore {
    ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command);

    ScheduledTaskView createHandlingTask(CreateHandlingTaskCommand command);

    ScheduledTaskView createWorkflowTask(CreateWorkflowTaskCommand command);
}
