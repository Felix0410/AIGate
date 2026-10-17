package com.felix.aigate.provider.adapter;

import com.felix.aigate.provider.entity.ProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 按 {@link ProviderType} 选择 {@link ProviderAdapter}，不做任何协议转换。
 *
 * <p>构造阶段用 EnumMap 建立索引，避免每次遍历；同一 ProviderType 被多个 Adapter
 * 注册时立即失败，绝不静默覆盖或使用默认 Adapter。不支持的类型明确抛异常。
 */
@Component
public class ProviderAdapterRegistry {

    private final Map<ProviderType, ProviderAdapter> adapters;

    public ProviderAdapterRegistry(List<ProviderAdapter> providerAdapters) {

        Map<ProviderType, ProviderAdapter> indexed = new EnumMap<>(ProviderType.class);

        for (ProviderAdapter adapter : providerAdapters) {
            ProviderAdapter previous = indexed.putIfAbsent(adapter.supportedType(), adapter);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate ProviderAdapter for type: " + adapter.supportedType());
            }
        }

        this.adapters = Map.copyOf(indexed);
    }

    public ProviderAdapter select(ProviderType type) {

        ProviderAdapter adapter = type == null ? null : adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No ProviderAdapter for type: " + type);
        }
        return adapter;
    }
}
