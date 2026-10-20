package com.felix.aigate.invocation;

import com.felix.aigate.invocation.config.InvocationSchedulingConfig;
import com.felix.aigate.invocation.schedule.InvocationRecordRetentionScheduler;
import com.felix.aigate.invocation.service.InvocationRecordRetentionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvocationRecordRetentionSchedulerTest {

    @Mock
    private InvocationRecordRetentionService retentionService;

    @InjectMocks
    private InvocationRecordRetentionScheduler scheduler;

    @Test
    @DisplayName("Scheduled 注解配置为每日 UTC 03:00")
    void scheduledAnnotationShouldBeUtcDailyThreeAm() throws NoSuchMethodException {

        Scheduled annotation = InvocationRecordRetentionScheduler.class
                .getMethod("cleanupExpiredRecords")
                .getAnnotation(Scheduled.class);

        assertNotNull(annotation, "@Scheduled annotation must be present");
        assertEquals("0 0 3 * * *", annotation.cron());
        assertEquals("UTC", annotation.zone());
    }

    @Test
    @DisplayName("InvocationSchedulingConfig 正确启用 Spring Scheduling")
    void invocationSchedulingConfigShouldEnableScheduling() {

        Configuration annotation = InvocationSchedulingConfig.class
                .getAnnotation(Configuration.class);
        assertNotNull(annotation);

        EnableScheduling enableScheduling = InvocationSchedulingConfig.class
                .getAnnotation(EnableScheduling.class);
        assertNotNull(enableScheduling, "@EnableScheduling must be present on InvocationSchedulingConfig");
    }

    @Test
    @DisplayName("正常清理：capture cutoff, 调用一次 deleteBefore")
    void normalCleanupShouldCaptureCutoffAndDelete() {

        when(retentionService.deleteBefore(any(Instant.class)))
                .thenReturn(3);

        Instant before = Instant.now();
        scheduler.cleanupExpiredRecords();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(retentionService, times(1)).deleteBefore(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();

        Instant lowerBound = before.minus(Duration.ofDays(30));
        Instant upperBound = after.minus(Duration.ofDays(30));

        assertTrue(() -> !cutoff.isBefore(lowerBound),
                "cutoff " + cutoff + " should be >= " + lowerBound);
        assertTrue(() -> !cutoff.isAfter(upperBound),
                "cutoff " + cutoff + " should be <= " + upperBound);
    }

    @Test
    @DisplayName("RetentionService 失败时异常不向外传播")
    void cleanupFailureShouldNotPropagate() {

        when(retentionService.deleteBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("sensitive database error"));

        assertDoesNotThrow(() -> scheduler.cleanupExpiredRecords());

        verify(retentionService, times(1)).deleteBefore(any(Instant.class));
    }
}