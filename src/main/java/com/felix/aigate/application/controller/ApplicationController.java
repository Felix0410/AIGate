package com.felix.aigate.application.controller;

import com.felix.aigate.application.dto.request.CreateApplicationRequest;
import com.felix.aigate.application.dto.request.UpdateApplicationRequest;
import com.felix.aigate.application.dto.response.ApplicationResponse;
import com.felix.aigate.application.entity.Application;
import com.felix.aigate.application.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ApplicationResponse createApplication(@Valid @RequestBody CreateApplicationRequest request) {

        Application application = applicationService.createApplication(
                request.getName(),
                request.getTeamId(),
                request.getDefaultDeploymentId()
        );

        return toResponse(application);
    }

    @GetMapping("/{id}")
    public ApplicationResponse getApplicationById(@PathVariable Long id) {
        return toResponse(applicationService.getApplicationById(id));
    }

    @GetMapping
    public List<ApplicationResponse> listApplications() {
        return applicationService.listApplications()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{id}")
    public ApplicationResponse updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationRequest request) {

        Application application = applicationService.updateApplication(
                id,
                request.getName(),
                request.getTeamId(),
                request.getDefaultDeploymentId()
        );

        return toResponse(application);
    }

    @DeleteMapping("/{id}")
    public void deleteApplication(@PathVariable Long id) {
        applicationService.deleteApplication(id);
    }

    private ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getName(),
                application.getTeamId(),
                application.getDefaultDeploymentId(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}