package com.felix.aigate.application.service;

import com.felix.aigate.application.entity.ApplicationApiKey;

/**
 * 创建 API Key 的内部结果：落库实体 + 仅返回一次的明文 Key。
 * 明文 apiKey 不进入 Entity，避免被持久化或误序列化。
 */
public record ApplicationApiKeyCreationResult(
        ApplicationApiKey applicationApiKey,
        String apiKey
) {
}
