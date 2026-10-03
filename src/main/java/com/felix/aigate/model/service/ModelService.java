package com.felix.aigate.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.common.exception.ConflictException;
import com.felix.aigate.common.exception.ResourceNotFoundException;
import com.felix.aigate.model.entity.Model;
import com.felix.aigate.model.mapper.ModelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelMapper modelMapper;

    public Model createModel(String name) {
        ensureModelNameAvailable(name, null);

        Model model = new Model();
        model.setName(name);

        modelMapper.insert(model);
        return modelMapper.selectById(model.getId());
    }

    public Model getModelById(Long id) {
        Model model = modelMapper.selectById(id);

        if (model == null) {
            throw new ResourceNotFoundException(
                    "MODEL_NOT_FOUND",
                    "Model not found"
            );
        }

        return model;
    }

    public List<Model> listModels() {
        return modelMapper.selectList(null);
    }

    public Model updateModel(Long id, String name) {
        Model model = getModelById(id);

        ensureModelNameAvailable(name, id);

        model.setName(name);

        modelMapper.updateById(model);
        return modelMapper.selectById(id);
    }

    public void deleteModel(Long id) {
        getModelById(id);
        modelMapper.deleteById(id);
    }

    private void ensureModelNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<Model> wrapper =
                new LambdaQueryWrapper<Model>()
                        .eq(Model::getName, name);

        if (excludeId != null) {
            wrapper.ne(Model::getId, excludeId);
        }

        Long count = modelMapper.selectCount(wrapper);

        if (count != null && count > 0) {
            throw new ConflictException(
                    "MODEL_NAME_ALREADY_EXISTS",
                    "Model name already exists"
            );
        }
    }
}