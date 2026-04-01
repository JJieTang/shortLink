package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.exception.GlobalExceptionHandler;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.service.ClickEventReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClickEventAdminControllerTest {

    private MockMvc mockMvc;
    private ClickEventReplayService clickEventReplayService;

    @BeforeEach
    void setUp() {
        clickEventReplayService = mock(ClickEventReplayService.class);
        ClickEventAdminController controller = new ClickEventAdminController(clickEventReplayService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldAcceptReplayRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/click-events/dlq/1774973958500-0/replay"))
                .andExpect(status().isAccepted());

        verify(clickEventReplayService).replayByMessageId("1774973958500-0");
    }

    @Test
    void shouldReturnNotFoundWhenDlqMessageDoesNotExist() throws Exception {
        doThrow(new ResourceNotFoundException("DLQ message not found: missing-id"))
                .when(clickEventReplayService)
                .replayByMessageId("missing-id");

        mockMvc.perform(post("/api/v1/admin/click-events/dlq/missing-id/replay"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("DLQ message not found: missing-id"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/click-events/dlq/missing-id/replay"));
    }
}
