package com.felix.aigate.invocation.service;

import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.provider.adapter.InvalidProviderResponseException;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import com.felix.aigate.runtime.exception.ProxyErrorType;
import com.felix.aigate.runtime.service.ModelInvocationException;
import com.felix.aigate.runtime.service.ModelProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvocationTrackingFacade {

    private final ModelProxyService modelProxyService;
    private final InvocationRecordMapper invocationRecordMapper;

    public InvokeResponse invoke(Long applicationId, InvokeRequest request) {

        long startNanos = System.nanoTime();

        InvokeResponse response;

        try {
            response = modelProxyService.invoke(applicationId, request);
        } catch (RuntimeException original) {

            long durationMs = elapsedMillis(startNanos);

            String errorCode = classifyErrorCode(original);

            saveBestEffort(
                    applicationId,
                    InvocationStatus.FAILED,
                    errorCode,
                    durationMs
            );

            throw original;
        }

        long durationMs = elapsedMillis(startNanos);

        saveBestEffort(
                applicationId,
                InvocationStatus.SUCCESS,
                null,
                durationMs
        );

        return response;
    }

    private long elapsedMillis(long startNanos) {

        long nanos = System.nanoTime() - startNanos;

        return Math.max(
                0L,
                TimeUnit.NANOSECONDS.toMillis(nanos)
        );
    }

    private String classifyErrorCode(RuntimeException exception) {

        if (exception instanceof ModelInvocationException modelException) {
            return modelException.getErrorType().getCode();
        }

        if (exception instanceof InvalidProviderResponseException) {
            return ProxyErrorType.PROVIDER_INVALID_RESPONSE.getCode();
        }

        return "INTERNAL_SERVER_ERROR";
    }

    private void saveBestEffort(
            Long applicationId,
            InvocationStatus status,
            String errorCode,
            long durationMs) {

        try {
            InvocationRecord record = new InvocationRecord();

            record.setApplicationId(applicationId);
            record.setStatus(status);
            record.setErrorCode(errorCode);
            record.setDurationMs(durationMs);
            record.setCreatedAt(Instant.now());

            invocationRecordMapper.insert(record);
        } catch (Exception ignored) {
            log.warn("Invocation record persistence failed");
        }
    }
}