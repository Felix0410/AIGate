package com.felix.aigate.provider.adapter;

import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ProviderAdapterRegistry 单元测试：按类型选择、不支持类型明确失败、重复注册启动即失败。
 * 不启 Spring、不连数据库。
 */
class ProviderAdapterRegistryTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("OPENAI_COMPATIBLE 选择到对应 Adapter")
    void shouldSelectOpenAiCompatibleAdapter() {

        OpenAiCompatibleProviderAdapter openAiAdapter = new OpenAiCompatibleProviderAdapter(jsonMapper);
        ProviderAdapterRegistry registry = new ProviderAdapterRegistry(List.of(openAiAdapter));

        assertSame(openAiAdapter, registry.select(ProviderType.OPENAI_COMPATIBLE));
    }

    @Test
    @DisplayName("没有对应 Adapter 时明确失败，不使用默认")
    void shouldFailWhenTypeUnsupported() {

        ProviderAdapterRegistry emptyRegistry = new ProviderAdapterRegistry(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> emptyRegistry.select(ProviderType.OPENAI_COMPATIBLE));

        ProviderAdapterRegistry registry =
                new ProviderAdapterRegistry(List.of(new OpenAiCompatibleProviderAdapter(jsonMapper)));

        assertThrows(IllegalArgumentException.class, () -> registry.select(null));
    }

    @Test
    @DisplayName("同一 ProviderType 重复注册 -> 构造阶段明确失败")
    void shouldFailOnDuplicateRegistration() {

        ProviderAdapter first = new StubAdapter(ProviderType.OPENAI_COMPATIBLE);
        ProviderAdapter second = new StubAdapter(ProviderType.OPENAI_COMPATIBLE);

        assertThrows(IllegalStateException.class,
                () -> new ProviderAdapterRegistry(List.of(first, second)));
    }

    /** 仅测试用的极简 Adapter，避免为测试新增生产类。 */
    private static final class StubAdapter implements ProviderAdapter {

        private final ProviderType type;

        private StubAdapter(ProviderType type) {
            this.type = type;
        }

        @Override
        public ProviderType supportedType() {
            return type;
        }

        @Override
        public Object buildRequest(InvokeRequest request, String remoteModelName) {
            throw new UnsupportedOperationException("test stub");
        }

        @Override
        public InvokeResponse parseResponse(String responseBody) {
            throw new UnsupportedOperationException("test stub");
        }
    }
}
