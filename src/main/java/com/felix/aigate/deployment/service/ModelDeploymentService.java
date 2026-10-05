package com.felix.aigate.deployment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.common.exception.ConflictException;
import com.felix.aigate.common.exception.ResourceNotFoundException;
import com.felix.aigate.credential.service.CredentialService;
import com.felix.aigate.deployment.entity.ModelDeployment;
import com.felix.aigate.deployment.mapper.ModelDeploymentMapper;
import com.felix.aigate.model.entity.Model;
import com.felix.aigate.model.mapper.ModelMapper;
import com.felix.aigate.provider.entity.Provider;
import com.felix.aigate.provider.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelDeploymentService {

    private final ModelDeploymentMapper modelDeploymentMapper;
    private final ProviderMapper providerMapper;
    private final ModelMapper modelMapper;
    private final CredentialService credentialService;

    public ModelDeployment createModelDeployment(
            String name,
            Long providerId,
            Long modelId,
            String endpointUrl,
            String remoteModelName,
            Boolean enabled,
            String credential
    ) {
        ensureProviderExists(providerId);
        ensureModelExists(modelId);
        ensureModelDeploymentNameAvailable(name, null);

        ModelDeployment deployment = new ModelDeployment();
        deployment.setName(name);
        deployment.setProviderId(providerId);
        deployment.setModelId(modelId);
        deployment.setEndpointUrl(endpointUrl);
        deployment.setRemoteModelName(remoteModelName);
        deployment.setEnabled(enabled);
        deployment.setEncryptedCredential(credentialService.encrypt(credential));

        modelDeploymentMapper.insert(deployment);

        return modelDeploymentMapper.selectById(deployment.getId());
    }

    public ModelDeployment getModelDeploymentById(Long id) {
        ModelDeployment deployment = modelDeploymentMapper.selectById(id);

        if (deployment == null) {
            throw new ResourceNotFoundException(
                    "MODEL_DEPLOYMENT_NOT_FOUND",
                    "Model deployment not found"
            );
        }

        return deployment;
    }

    public List<ModelDeployment> listModelDeployments() {
        return modelDeploymentMapper.selectList(null);
    }

    public ModelDeployment updateModelDeployment(
            Long id,
            String name,
            Long providerId,
            Long modelId,
            String endpointUrl,
            String remoteModelName,
            Boolean enabled,
            String credential
    ) {
        ModelDeployment deployment = getModelDeploymentById(id);

        ensureProviderExists(providerId);
        ensureModelExists(modelId);
        ensureModelDeploymentNameAvailable(name, id);

        deployment.setName(name);
        deployment.setProviderId(providerId);
        deployment.setModelId(modelId);
        deployment.setEndpointUrl(endpointUrl);
        deployment.setRemoteModelName(remoteModelName);
        deployment.setEnabled(enabled);
        deployment.setEncryptedCredential(credentialService.encrypt(credential));

        modelDeploymentMapper.updateById(deployment);

        return modelDeploymentMapper.selectById(id);
    }

    public void deleteModelDeployment(Long id) {
        getModelDeploymentById(id);
        modelDeploymentMapper.deleteById(id);
    }

    private void ensureProviderExists(Long providerId) {
        Provider provider = providerMapper.selectById(providerId);

        if (provider == null) {
            throw new ResourceNotFoundException(
                    "PROVIDER_NOT_FOUND",
                    "Provider not found"
            );
        }
    }

    private void ensureModelExists(Long modelId) {
        Model model = modelMapper.selectById(modelId);

        if (model == null) {
            throw new ResourceNotFoundException(
                    "MODEL_NOT_FOUND",
                    "Model not found"
            );
        }
    }

    private void ensureModelDeploymentNameAvailable(
            String name,
            Long excludeId
    ) {
        LambdaQueryWrapper<ModelDeployment> wrapper =
                new LambdaQueryWrapper<ModelDeployment>()
                        .eq(ModelDeployment::getName, name);

        if (excludeId != null) {
            wrapper.ne(ModelDeployment::getId, excludeId);
        }

        Long count = modelDeploymentMapper.selectCount(wrapper);

        if (count != null && count > 0) {
            throw new ConflictException(
                    "MODEL_DEPLOYMENT_NAME_ALREADY_EXISTS",
                    "Model deployment name already exists"
            );
        }
    }
}