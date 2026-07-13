package com.serviceos.task.application;

import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;

public interface TaskSchedulingStore {
    ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command);

    ScheduledTaskView createHandlingTask(CreateHandlingTaskCommand command);
}
