package com.serviceos.operations.api;

public interface OperationalExceptionService {
    OperationalExceptionView openFromTaskFailure(OpenTaskFailureCommand command);
}
