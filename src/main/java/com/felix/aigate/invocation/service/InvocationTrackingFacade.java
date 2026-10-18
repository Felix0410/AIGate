package com.felix.aigate.invocation.service;

import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
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

        InvokeResponse response =
                modelProxyService.invoke(applicationId, request);

        long elapsedNanos = System.nanoTime() - startNanos;

        long durationMs = Math.max(
                0L,
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos));

        InvocationRecord record = new InvocationRecord();

        record.setApplicationId(applicationId);
        record.setStatus(InvocationStatus.SUCCESS);
        record.setErrorCode(null);
        record.setDurationMs(durationMs);
        record.setCreatedAt(Instant.now());

        try {
            invocationRecordMapper.insert(record);
        } catch (Exception ignored) {
            log.warn("Invocation record persistence failed");
        }

        return response;
    }
}