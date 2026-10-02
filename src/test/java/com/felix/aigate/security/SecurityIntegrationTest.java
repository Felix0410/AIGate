package com.felix.aigate.security;

import com.felix.aigate.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 Spring Security 链路：匿名被拦截返回 401，携带正确 Basic 凭证可通过。
 */
class SecurityIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("匿名访问受保护接口 -> 401 UNAUTHORIZED")
    void anonymousRequestShouldReturn401() throws Exception {

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("携带正确 Basic 凭证访问 -> 200")
    void authenticatedRequestShouldPassSecurity() throws Exception {

        mockMvc.perform(get("/api/teams")
                        .with(httpBasic(TEST_USERNAME, TEST_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("错误凭证访问受保护接口 -> 401 UNAUTHORIZED")
    void wrongCredentialsShouldReturn401() throws Exception {

        mockMvc.perform(get("/api/teams")
                        .with(httpBasic(TEST_USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
