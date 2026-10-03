package com.felix.aigate.model.controller;

import com.felix.aigate.model.dto.request.CreateModelRequest;
import com.felix.aigate.model.dto.request.UpdateModelRequest;
import com.felix.aigate.model.dto.response.ModelResponse;
import com.felix.aigate.model.entity.Model;
import com.felix.aigate.model.service.ModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    @PostMapping
    public ModelResponse createModel(@Valid @RequestBody CreateModelRequest request) {
        Model model = modelService.createModel(request.getName());
        return toResponse(model);
    }

    @GetMapping("/{id}")
    public ModelResponse getModelById(@PathVariable Long id) {
        return toResponse(modelService.getModelById(id));
    }

    @GetMapping
    public List<ModelResponse> listModels() {
        return modelService.listModels()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{id}")
    public ModelResponse updateModel(
            @PathVariable Long id,
            @Valid @RequestBody UpdateModelRequest request
    ) {
        Model model = modelService.updateModel(id, request.getName());
        return toResponse(model);
    }

    @DeleteMapping("/{id}")
    public void deleteModel(@PathVariable Long id) {
        modelService.deleteModel(id);
    }

    private ModelResponse toResponse(Model model) {
        return new ModelResponse(
                model.getId(),
                model.getName(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}