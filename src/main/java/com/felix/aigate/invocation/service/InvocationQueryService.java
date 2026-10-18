package com.felix.aigate.invocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.application.service.ApplicationService;
import com.felix.aigate.invocation.dto.InvocationRecordResponse;
import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvocationQueryService {

    private final ApplicationService applicationService;
    private final InvocationRecordMapper invocationRecordMapper;

    public List<InvocationRecordResponse> listRecent(Long applicationId, int limit) {

        applicationService.getApplicationById(applicationId);

        if (limit < 1 || limit > 100) {
            throw new com.felix.aigate.invocation.exception.InvalidInvocationQueryException();
        }

        LambdaQueryWrapper<InvocationRecord> wrapper = new LambdaQueryWrapper<InvocationRecord>()
                .eq(InvocationRecord::getApplicationId, applicationId)
                .orderByDesc(InvocationRecord::getId)
                .last("LIMIT " + limit);

        List<InvocationRecord> records = invocationRecordMapper.selectList(wrapper);

        return records.stream()
                .map(this::toResponse)
                .toList();
    }

    private InvocationRecordResponse toResponse(InvocationRecord record) {
        return new InvocationRecordResponse(
                record.getId(),
                record.getApplicationId(),
                record.getStatus(),
                record.getErrorCode(),
                record.getDurationMs(),
                record.getCreatedAt()
        );
    }
}