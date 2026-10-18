package com.felix.aigate.invocation.service;

import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InvocationRecordRetentionService {

    private final InvocationRecordMapper invocationRecordMapper;

    public int deleteBefore(Instant cutoff) {
        return invocationRecordMapper.deleteCreatedBefore(cutoff);
    }
}