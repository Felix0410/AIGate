package com.felix.aigate.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * 集成测试基类。
 *
 * <p>负责：启动 Spring Boot 测试上下文、注入 MockMvc 与 JsonMapper、
 * 复用 {@link MySqlTestContainer} 提供的真实 MySQL，并动态覆盖 datasource 与安全凭证。
 *
 * <p>子类只写具体业务测试，不要把业务逻辑放进基类。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    /** 测试用 HTTP Basic 凭证，仅存在于测试上下文，非生产凭证。 */
    protected static final String TEST_USERNAME = "test-admin";
    protected static final String TEST_PASSWORD = "test-password";

    /** 测试用 AES 主密钥：固定 32 字节的 Base64，仅用于测试上下文，不依赖本机真实环境变量。 */
    protected static final String TEST_MASTER_KEY =
            Base64.getEncoder().encodeToString(new byte[32]);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestContainer.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MySqlTestContainer.MYSQL::getUsername);
        registry.add("spring.datasource.password", MySqlTestContainer.MYSQL::getPassword);
    }

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.user.name", () -> TEST_USERNAME);
        registry.add("spring.security.user.password", () -> TEST_PASSWORD);
    }

    @DynamicPropertySource
    static void credentialProperties(DynamicPropertyRegistry registry) {
        registry.add("AIGATE_MASTER_KEY", () -> TEST_MASTER_KEY);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    /** 为任意请求附加测试用 HTTP Basic 凭证。 */
    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.with(httpBasic(TEST_USERNAME, TEST_PASSWORD));
    }

    /** 使用 Spring 上下文配置的 JsonMapper 序列化请求体。 */
    protected String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }
}
