package com.felix.aigate.deployment.controller;

import com.felix.aigate.deployment.dto.request.CreateModelDeploymentRequest;
import com.felix.aigate.deployment.dto.request.UpdateModelDeploymentRequest;
import com.felix.aigate.deployment.dto.response.ModelDeploymentResponse;
import com.felix.aigate.deployment.entity.ModelDeployment;
import com.felix.aigate.deployment.service.ModelDeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/model-deployments")
@RequiredArgsConstructor
public class ModelDeploymentController {

    private final ModelDeploymentService modelDeploymentService;

    @PostMapping
    public ModelDeploymentResponse createModelDeployment(
            @Valid @RequestBody CreateModelDeploymentRequest request
    ) {
        ModelDeployment deployment =
                modelDeploymentService.createModelDeployment(
                        request.getName(),
                        request.getProviderId(),
                        request.getModelId(),
                        request.getEndpointUrl(),
                        request.getRemoteModelName(),
                        request.getEnabled()
                );

        return toResponse(deployment);
    }

    @GetMapping("/{id}")
    public ModelDeploymentResponse getModelDeploymentById(
            @PathVariable Long id
    ) {
        return toResponse(
                modelDeploymentService.getModelDeploymentById(id)
        );
    }

    @GetMapping
    public List<ModelDeploymentResponse> listModelDeployments() {
        return modelDeploymentService.listModelDeployments()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{id}")
    public ModelDeploymentResponse updateModelDeployment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateModelDeploymentRequest request
    ) {
        ModelDeployment deployment =
                modelDeploymentService.updateModelDeployment(
                        id,
                        request.getName(),
                        request.getProviderId(),
                        request.getModelId(),
                        request.getEndpointUrl(),
                        request.getRemoteModelName(),
                        request.getEnabled()
                );

        return toResponse(deployment);
    }

    @DeleteMapping("/{id}")
    public void deleteModelDeployment(@PathVariable Long id) {
        modelDeploymentService.deleteModelDeployment(id);
    }

    private ModelDeploymentResponse toResponse(
            ModelDeployment deployment
    ) {
        return new ModelDeploymentResponse(
                deployment.getId(),
                deployment.getName(),
                deployment.getProviderId(),
                deployment.getModelId(),
                deployment.getEndpointUrl(),
                deployment.getRemoteModelName(),
                deployment.getEnabled(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt()
        );
    }
}