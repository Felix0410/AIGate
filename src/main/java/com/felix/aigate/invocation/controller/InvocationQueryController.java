package com.felix.aigate.invocation.controller;

import com.felix.aigate.invocation.dto.InvocationRecordResponse;
import com.felix.aigate.invocation.exception.InvalidInvocationQueryException;
import com.felix.aigate.invocation.service.InvocationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class InvocationQueryController {

    private final InvocationQueryService invocationQueryService;

    @GetMapping("/{applicationId}/invocations")
    public List<InvocationRecordResponse> listRecent(
            @PathVariable Long applicationId,
            @RequestParam(required = false) String limit) {

        int parsedLimit = parseLimit(limit);
        return invocationQueryService.listRecent(applicationId, parsedLimit);
    }

    private int parseLimit(String rawLimit) {

        if (rawLimit == null) {
            return 20;
        }

        if (!rawLimit.matches("[0-9]+")) {
            throw new InvalidInvocationQueryException();
        }

        int value;
        try {
            value = Integer.parseInt(rawLimit);
        } catch (NumberFormatException e) {
            throw new InvalidInvocationQueryException();
        }

        if (value < 1 || value > 100) {
            throw new InvalidInvocationQueryException();
        }

        return value;
    }
}