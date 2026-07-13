package com.serviceos.task.api;

public interface TaskSchedulingService {
    ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command);

    ScheduledTaskView createHandlingTask(CreateHandlingTaskCommand command);
}
