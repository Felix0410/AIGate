package com.felix.aigate.provider.adapter;

import com.felix.aigate.provider.entity.ProviderType;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;

/**
 * Provider 协议转换器：只做统一契约与 Provider 协议之间的双向转换。
 *
 * <p>不负责 HTTP、数据库读取、凭证解密、URL 或 Authorization Header。
 * buildRequest 返回值的实际类型由具体 Adapter 决定（当前为 OpenAiChatRequest），
 * 交由调用方（P2-T08 ModelProxyService）用 Jackson 序列化后发送。
 */
public interface ProviderAdapter {

    ProviderType supportedType();

    Object buildRequest(InvokeRequest request, String remoteModelName);

    InvokeResponse parseResponse(String responseBody);
}
