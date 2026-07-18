package com.serviceos.task.api;

public interface TaskSchedulingService {
    ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command);

    ScheduledTaskView createHandlingTask(CreateHandlingTaskCommand command);

    HandlingTaskCancellationReceipt cancelHandlingTask(CancelHandlingTaskCommand command);

    HandlingTaskCompletionReceipt completeHandlingTask(CompleteHandlingTaskCommand command);

    ScheduledTaskView createWorkflowTask(CreateWorkflowTaskCommand command);
}
