package com.felix.aigate.invocation.schedule;

import com.felix.aigate.invocation.service.InvocationRecordRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvocationRecordRetentionScheduler {

    private final InvocationRecordRetentionService retentionService;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void cleanupExpiredRecords() {

        Instant cutoff = Instant.now().minus(Duration.ofDays(30));

        try {
            int deleted = retentionService.deleteBefore(cutoff);
            log.info("Retention cleanup deleted {} expired records", deleted);
        } catch (Exception ignored) {
            log.warn("Invocation record retention failed");
        }
    }
}