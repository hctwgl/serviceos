package com.serviceos.bootstrap;

import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.shared.ProblemCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * RFC 9457 Problem Details 统一出口。响应只暴露稳定错误码和 correlation，不回传内部异常栈。
 */
@RestControllerAdvice
final class GlobalProblemHandler {

    @ExceptionHandler(BusinessProblem.class)
    ProblemDetail handleBusinessProblem(BusinessProblem exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case IDEMPOTENCY_KEY_REUSED, IDEMPOTENCY_IN_PROGRESS,
                 FILE_UPLOAD_CONFLICT, FILE_FINALIZE_IN_PROGRESS,
                 TASK_STATE_CONFLICT, VERSION_CONFLICT -> HttpStatus.CONFLICT;
            case FILE_UPLOAD_EXPIRED -> HttpStatus.GONE;
            case FILE_NOT_AVAILABLE -> HttpStatus.LOCKED;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return problem(status, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ProblemDetail handleValidation(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ProblemCode.VALIDATION_FAILED,
                "The request is invalid", request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleDuplicate(DuplicateKeyException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ProblemCode.PROJECT_CODE_ALREADY_EXISTS,
                "A project with the same code already exists", request);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            ProblemCode code,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://serviceos.example/problems/" + code.name().toLowerCase()));
        problem.setTitle(code.name());
        problem.setProperty("errorCode", code.name());
        problem.setProperty("correlationId", CorrelationIds.fromRequestAttribute(
                request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE)));
        return problem;
    }
}
