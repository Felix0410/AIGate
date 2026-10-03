package com.felix.aigate.provider.controller;

import com.felix.aigate.provider.dto.request.CreateProviderRequest;
import com.felix.aigate.provider.dto.request.UpdateProviderRequest;
import com.felix.aigate.provider.dto.response.ProviderResponse;
import com.felix.aigate.provider.entity.Provider;
import com.felix.aigate.provider.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @PostMapping
    public ProviderResponse createProvider(
            @Valid @RequestBody CreateProviderRequest request
    ) {
        Provider provider = providerService.createProvider(
                request.getName(),
                request.getType()
        );

        return toResponse(provider);
    }

    @GetMapping("/{id}")
    public ProviderResponse getProviderById(@PathVariable Long id) {
        Provider provider = providerService.getProviderById(id);
        return toResponse(provider);
    }

    @GetMapping
    public List<ProviderResponse> listProviders() {
        return providerService.listProviders()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{id}")
    public ProviderResponse updateProvider(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProviderRequest request
    ) {
        Provider provider = providerService.updateProvider(
                id,
                request.getName(),
                request.getType()
        );

        return toResponse(provider);
    }

    @DeleteMapping("/{id}")
    public void deleteProvider(@PathVariable Long id) {
        providerService.deleteProvider(id);
    }

    private ProviderResponse toResponse(Provider provider) {
        return new ProviderResponse(
                provider.getId(),
                provider.getName(),
                provider.getType(),
                provider.getCreatedAt(),
                provider.getUpdatedAt()
        );
    }
}