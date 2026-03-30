package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UrlCrudIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
    }

    @Test
    void shouldCreateGetListAndDeleteUrls() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/second",
                                  "customAlias": "second-link"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("second-link"));

        mockMvc.perform(get("/api/v1/urls/first-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("first-link"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/first"));

        mockMvc.perform(get("/api/v1/urls")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(delete("/api/v1/urls/first-link"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/urls/first-link"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
