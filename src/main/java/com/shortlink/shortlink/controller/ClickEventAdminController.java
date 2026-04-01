package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.service.ClickEventReplayService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/click-events")
public class ClickEventAdminController {

    private final ClickEventReplayService clickEventReplayService;

    public ClickEventAdminController(ClickEventReplayService clickEventReplayService) {
        this.clickEventReplayService = clickEventReplayService;
    }

    @GetMapping("/dlq")
    public List<ClickEventReplayService.DlqMessageView> listDlqMessages() {
        return clickEventReplayService.listDlqMessages();
    }

    @PostMapping("/dlq/{messageId}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void replayDlqMessage(@PathVariable String messageId) {
        clickEventReplayService.replayByMessageId(messageId);
    }
}
