package com.example.campusflow_backend.exception;

import com.example.campusflow_backend.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "The change conflicts with existing records.", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(field ->
                fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Request validation failed.", request, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ApiError> invalidRequest(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request body or parameters.", request, Map.of());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException exception,
                                                      HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(field ->
                        fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error ->
                        fields.putIfAbsent(name == null ? "parameter" : name, error.getDefaultMessage()));
            }
        });
        return error(HttpStatus.BAD_REQUEST, "Request validation failed.", request, fields);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> missingRoute(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Resource not found.", request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported.", request, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMediaType(HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported.", request, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> notAcceptable(HttpServletRequest request) {
        return error(HttpStatus.NOT_ACCEPTABLE, "Requested response type not supported.", request, Map.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> invalidInput(InvalidRequestException exception,HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,exception.getMessage(),request,Map.of());
    }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiError> authentication(HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED,"Authentication required or invalid credentials.",request,Map.of());
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN,"Access denied.",request,Map.of());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(HttpServletRequest request) {
        // Do not log request bodies, credentials, or database exception details.
        log.error("Unexpected server error while handling an API request");
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.", request, Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message,
                                          HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI(), fields));
    }
}
