package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UrlCrudIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String OTHER_EMAIL = "other@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    private String ownerAccessToken;
    private String otherAccessToken;

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
        userRepository.findByEmail(OWNER_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OTHER_EMAIL).ifPresent(userRepository::delete);
        ownerAccessToken = issueAccessToken(OWNER_EMAIL, "Owner");
        otherAccessToken = issueAccessToken(OTHER_EMAIL, "Other User");
    }

    @Test
    void shouldCreateGetListAndDeleteUrls() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/first",
                                  "customAlias": "first-link"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("first-link"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/first-link"));

        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/second",
                                  "customAlias": "second-link"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("second-link"));

        mockMvc.perform(get("/api/v1/urls/first-link")
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("first-link"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/first"));

        mockMvc.perform(get("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken))
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(delete("/api/v1/urls/first-link")
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/urls/first-link")
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void shouldRequireAuthenticationForUrlManagementEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/urls"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    void shouldHideUrlsOwnedByAnotherUser() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/private",
                                  "customAlias": "private-link"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/urls/private-link")
                        .header("Authorization", bearer(otherAccessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
