package com.felix.aigate.application.controller;

import com.felix.aigate.application.dto.request.CreateApplicationApiKeyRequest;
import com.felix.aigate.application.dto.response.ApplicationApiKeyResponse;
import com.felix.aigate.application.dto.response.CreateApplicationApiKeyResponse;
import com.felix.aigate.application.entity.ApplicationApiKey;
import com.felix.aigate.application.service.ApplicationApiKeyCreationResult;
import com.felix.aigate.application.service.ApplicationApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/api-keys")
@RequiredArgsConstructor
public class ApplicationApiKeyController {

    private final ApplicationApiKeyService applicationApiKeyService;

    @PostMapping
    public CreateApplicationApiKeyResponse createApiKey(
            @PathVariable Long applicationId,
            @Valid @RequestBody CreateApplicationApiKeyRequest request) {

        ApplicationApiKeyCreationResult result =
                applicationApiKeyService.createApiKey(applicationId, request.getName());

        return toCreateResponse(result);
    }

    @GetMapping
    public List<ApplicationApiKeyResponse> listApiKeys(@PathVariable Long applicationId) {
        return applicationApiKeyService.listApiKeys(applicationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{id}/revoke")
    public ApplicationApiKeyResponse revokeApiKey(
            @PathVariable Long applicationId,
            @PathVariable Long id) {

        return toResponse(applicationApiKeyService.revokeApiKey(applicationId, id));
    }

    private ApplicationApiKeyResponse toResponse(ApplicationApiKey apiKey) {
        return new ApplicationApiKeyResponse(
                apiKey.getId(),
                apiKey.getApplicationId(),
                apiKey.getName(),
                apiKey.getKeyId(),
                apiKey.getKeyPrefix(),
                apiKey.getStatus(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt()
        );
    }

    private CreateApplicationApiKeyResponse toCreateResponse(ApplicationApiKeyCreationResult result) {
        ApplicationApiKey apiKey = result.applicationApiKey();
        return new CreateApplicationApiKeyResponse(
                apiKey.getId(),
                apiKey.getApplicationId(),
                apiKey.getName(),
                apiKey.getKeyId(),
                apiKey.getKeyPrefix(),
                apiKey.getStatus(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt(),
                result.apiKey()
        );
    }
}
