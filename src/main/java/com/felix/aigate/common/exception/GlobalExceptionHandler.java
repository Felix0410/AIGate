package com.felix.aigate.common.exception;

import com.felix.aigate.invocation.exception.InvalidInvocationQueryException;
import com.felix.aigate.common.response.ApiErrorResponse;
import com.felix.aigate.provider.adapter.InvalidProviderResponseException;
import com.felix.aigate.runtime.exception.ProxyErrorType;
import com.felix.aigate.runtime.service.ModelInvocationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        ApiErrorResponse response = new ApiErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                Instant.now(),
                errors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                exception.getCode(),
                exception.getMessage(),
                Instant.now(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                exception.getCode(),
                exception.getMessage(),
                Instant.now(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey(
            DuplicateKeyException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "RESOURCE_CONFLICT",
                "Resource already exists",
                Instant.now(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "RESOURCE_NOT_FOUND",
                "Resource not found",
                Instant.now(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "RESOURCE_CONFLICT",
                "Resource is referenced by other data",
                Instant.now(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "INVALID_REQUEST",
                "Request body is invalid",
                Instant.now(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    /**
     * 模型代理业务异常：按 {@link ProxyErrorType} 映射为固定错误码 + 脱敏消息 + 对应 HTTP 状态。
     *
     * <p>日志只输出安全错误码，不打印异常消息 / cause 链，避免泄露上游 URL、Credential、响应体。
     * 明确不走兜底 {@link #handleUnexpectedException} 的全栈打印分支。
     */
    @ExceptionHandler(ModelInvocationException.class)
    public ResponseEntity<ApiErrorResponse> handleModelInvocation(
            ModelInvocationException exception) {

        ProxyErrorType type = exception.getErrorType();
        log.warn("Model invocation failed: code={}", type.getCode());

        ApiErrorResponse response = new ApiErrorResponse(
                type.getCode(),
                type.getMessage(),
                Instant.now(),
                null
        );

        return ResponseEntity.status(type.getHttpStatus()).body(response);
    }

    /**
     * Provider HTTP 200 但响应结构不合法：统一映射为 502 PROVIDER_INVALID_RESPONSE。
     * 忽略异常自身携带的结构描述，只返回固定安全信息。
     */
    @ExceptionHandler(InvalidProviderResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidProviderResponse(
            InvalidProviderResponseException exception) {

        ProxyErrorType type = ProxyErrorType.PROVIDER_INVALID_RESPONSE;
        log.warn("Invalid provider response: mapped to code={}", type.getCode());

        ApiErrorResponse response = new ApiErrorResponse(
                type.getCode(),
                type.getMessage(),
                Instant.now(),
                null
        );

        return ResponseEntity.status(type.getHttpStatus()).body(response);
    }

    @ExceptionHandler(InvalidInvocationQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidInvocationQuery(
            InvalidInvocationQueryException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "INVALID_REQUEST",
                "Invalid invocation query limit",
                Instant.now(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception) {

        log.error("Unexpected exception", exception);

        ApiErrorResponse response = new ApiErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                Instant.now(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}