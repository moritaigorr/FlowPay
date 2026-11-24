package com.flowpay.api;

import com.flowpay.api.ApiProblem.FieldViolation;
import com.flowpay.observability.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Maps controller and application failures to a stable RFC 9457 response shape. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiProblem> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        var status = HttpStatus.valueOf(exception.getStatusCode().value());
        var detail = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return response(status, detail, request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> handleInvalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::fieldViolation)
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "Revise os campos informados.", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiProblem> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        var violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "Revise os campos informados.", request, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiProblem> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "A requisição contém dados ou parâmetros inválidos.", request, List.of());
    }

    private static String frameworkDetail(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "A requisição contém dados ou parâmetros inválidos.";
            case NOT_FOUND -> "O recurso solicitado não foi encontrado.";
            case METHOD_NOT_ALLOWED -> "O método não é permitido para este recurso.";
            case TOO_MANY_REQUESTS -> "Limite de tentativas atingido. Tente novamente mais tarde.";
            default -> "A requisição não pôde ser processada.";
        };
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse frameworkError) {
            var status = HttpStatus.valueOf(frameworkError.getStatusCode().value());
            return response(status, frameworkDetail(status), request, List.of());
        }
        var requestId = requestId(request);
        LOGGER.error("api_request_failed request_id={} exception_type={}", requestId,
                exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno. Tente novamente.", request, List.of());
    }

    private static FieldViolation fieldViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getCode() == null ? "Invalid" : error.getCode(),
                error.getDefaultMessage() == null ? "Valor inválido." : error.getDefaultMessage());
    }

    private static ResponseEntity<ApiProblem> response(HttpStatus status, String detail,
                                                        HttpServletRequest request,
                                                        List<FieldViolation> violations) {
        var body = new ApiProblem(ABOUT_BLANK, status.getReasonPhrase(), status.value(), detail,
                request.getRequestURI(), requestId(request), violations);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static String requestId(HttpServletRequest request) {
        var requestId = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        return requestId instanceof String value ? value : null;
    }

}
