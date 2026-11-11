package com.flowpay.config.web;

import com.flowpay.company.CompanyUnavailableException;
import com.flowpay.company.ContractValidationException;
import com.flowpay.identity.InvalidCredentialsException;
import com.flowpay.identity.OperatorUnavailableException;
import com.flowpay.customer.CustomerNotFoundException;
import com.flowpay.customer.DemoLimitReachedException;
import com.flowpay.customer.ContentionTimeoutException;
import com.flowpay.config.demo.DemoCapacityReachedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ApiProblems problems;

    ApiExceptionHandler(ApiProblems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ContractValidationException.class)
    ProblemDetail validation(ContractValidationException exception, HttpServletRequest request) {
        var problem = problems.create(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.");
        problem.setProperty("fieldErrors", exception.fieldErrors());
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail credentials(HttpServletRequest request) {
        return problems.create(request, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail customerNotFound(HttpServletRequest request) {
        return problems.create(request, HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer was not found.");
    }

    @ExceptionHandler(com.flowpay.payment.PaymentNotFoundException.class)
    ProblemDetail paymentNotFound(HttpServletRequest request) {
        return problems.create(request, HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment was not found.");
    }

    @ExceptionHandler(com.flowpay.payment.PaymentFinalStateException.class)
    ProblemDetail paymentFinalState(HttpServletRequest request) {
        return problems.create(request, HttpStatus.CONFLICT, "PAYMENT_FINAL_STATE", "A final payment cannot change status.");
    }

    @ExceptionHandler(DemoLimitReachedException.class)
    ProblemDetail customerLimit(HttpServletRequest request) {
        return problems.create(request, HttpStatus.CONFLICT, "DEMO_LIMIT_REACHED", "The demo customer limit has been reached.");
    }

    @ExceptionHandler(ContentionTimeoutException.class)
    ResponseEntity<ProblemDetail> customerContention(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
                .body(problems.create(request, HttpStatus.SERVICE_UNAVAILABLE, "CONTENTION_TIMEOUT",
                        "The operation could not acquire its lock. Please retry."));
    }

    @ExceptionHandler(com.flowpay.payment.IdempotencyKeyReusedException.class)
    ProblemDetail paymentKeyReused(HttpServletRequest request) {
        return problems.create(request, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "The idempotency key was used for a different request.");
    }

    @ExceptionHandler(com.flowpay.payment.DemoLimitReachedException.class)
    ProblemDetail paymentLimit(HttpServletRequest request) {
        return problems.create(request, HttpStatus.CONFLICT, "DEMO_LIMIT_REACHED", "The demo payment limit has been reached.");
    }

    @ExceptionHandler(DemoCapacityReachedException.class)
    ResponseEntity<ProblemDetail> demoCapacity(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "60")
                .body(problems.create(request, HttpStatus.SERVICE_UNAVAILABLE, "DEMO_CAPACITY_REACHED",
                        "The demo service is at its active capacity. Please try again later."));
    }

    @ExceptionHandler(com.flowpay.payment.ContentionTimeoutException.class)
    ResponseEntity<ProblemDetail> paymentContention(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
                .body(problems.create(request, HttpStatus.SERVICE_UNAVAILABLE, "CONTENTION_TIMEOUT",
                        "The operation could not acquire its lock. Please retry the same operation."));
    }

    @ExceptionHandler({CompanyUnavailableException.class, OperatorUnavailableException.class})
    ProblemDetail unavailable(RuntimeException exception, HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        boolean expired = exception instanceof CompanyUnavailableException company
                && company.reason() == CompanyUnavailableException.Reason.EXPIRED_DEMO;
        return problems.create(request, HttpStatus.UNAUTHORIZED, expired ? "DEMO_EXPIRED" : "UNAUTHENTICATED",
                expired ? "The demo has expired." : "Authentication is required.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest webRequest) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String code = switch (status) {
            case BAD_REQUEST -> "VALIDATION_FAILED";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "INTERNAL_ERROR";
        };
        var request = ((ServletWebRequest) webRequest).getRequest();
        ProblemDetail problem = problems.create(request, status, code,
                "The request could not be processed.");
        if (status == HttpStatus.BAD_REQUEST) {
            Throwable cause = exception;
            while (cause.getCause() != null) cause = cause.getCause();
            boolean unknown = cause instanceof UnrecognizedPropertyException;
            String field = unknown ? ((UnrecognizedPropertyException) cause).getPropertyName() : "$";
            problem.setProperty("fieldErrors", java.util.List.of(java.util.Map.of(
                    "field", field, "code", unknown ? "UNKNOWN_FIELD" : "INVALID_FORMAT", "message", "Invalid request format.")));
        }
        return new ResponseEntity<>(problem, headers, status);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        // Exception messages and stack traces may contain SQL, credentials or user input.
        LOG.error("Request failed with exception type {}", exception.getClass().getName());
        return problems.create(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "An unexpected error occurred.");
    }
}
