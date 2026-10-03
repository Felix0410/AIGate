package com.felix.aigate.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.common.exception.ConflictException;
import com.felix.aigate.common.exception.ResourceNotFoundException;
import com.felix.aigate.provider.entity.Provider;
import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.provider.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderService {

    private final ProviderMapper providerMapper;

    public Provider createProvider(String name, ProviderType type) {
        ensureProviderNameAvailable(name, null);

        Provider provider = new Provider();
        provider.setName(name);
        provider.setType(type);

        providerMapper.insert(provider);
        return providerMapper.selectById(provider.getId());
    }

    public Provider getProviderById(Long id) {
        Provider provider = providerMapper.selectById(id);

        if (provider == null) {
            throw new ResourceNotFoundException(
                    "PROVIDER_NOT_FOUND",
                    "Provider not found"
            );
        }

        return provider;
    }

    public List<Provider> listProviders() {
        return providerMapper.selectList(null);
    }

    public Provider updateProvider(Long id, String name, ProviderType type) {
        Provider provider = getProviderById(id);

        ensureProviderNameAvailable(name, id);

        provider.setName(name);
        provider.setType(type);

        providerMapper.updateById(provider);
        return providerMapper.selectById(id);
    }

    public void deleteProvider(Long id) {
        getProviderById(id);
        providerMapper.deleteById(id);
    }

    private void ensureProviderNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<Provider> wrapper =
                new LambdaQueryWrapper<Provider>()
                        .eq(Provider::getName, name);

        if (excludeId != null) {
            wrapper.ne(Provider::getId, excludeId);
        }

        Long count = providerMapper.selectCount(wrapper);

        if (count != null && count > 0) {
            throw new ConflictException(
                    "PROVIDER_NAME_ALREADY_EXISTS",
                    "Provider name already exists"
            );
        }
    }
}