package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RedirectFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
    }

    @Test
    void shouldRedirectWith302AfterUrlCreation() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/landing-page",
                                  "customAlias": "launch-page"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/launch-page"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing-page"));
    }
}
