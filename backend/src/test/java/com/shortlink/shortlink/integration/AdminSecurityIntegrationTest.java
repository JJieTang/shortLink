package com.shortlink.shortlink.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminSecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String USER_EMAIL = "member@example.com";

    @Autowired
    private MockMvc mockMvc;

    private String adminAccessToken;
    private String userAccessToken;

    @BeforeEach
    void setUp() {
        adminAccessToken = issueAccessToken(ADMIN_EMAIL, "Admin User", "ADMIN");
        userAccessToken = issueAccessToken(USER_EMAIL, "Regular User");
    }

    @Test
    void shouldRequireAuthenticationForAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/click-events/dlq"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    void shouldForbidNonAdminUsersFromAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/click-events/dlq")
                        .header("Authorization", bearer(userAccessToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminsToAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/click-events/dlq")
                        .header("Authorization", bearer(adminAccessToken)))
                .andExpect(status().isOk());
    }
}
