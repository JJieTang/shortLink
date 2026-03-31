package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.ClickEvent;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.repository.ClickEventRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickEventConsumerTest {

    private ClickEventRepository clickEventRepository;
    private UrlRepository urlRepository;
    private UserAgentParser userAgentParser;
    private GeoLookupService geoLookupService;
    private ClickEventConsumer clickEventConsumer;

    @BeforeEach
    void setUp() {
        clickEventRepository = mock(ClickEventRepository.class);
        urlRepository = mock(UrlRepository.class);
        userAgentParser = mock(UserAgentParser.class);
        geoLookupService = mock(GeoLookupService.class);

        clickEventConsumer = new ClickEventConsumer(
                clickEventRepository,
                urlRepository,
                userAgentParser,
                geoLookupService
        );
    }

    @Test
    void shouldSkipDuplicateEvent() {
        ClickEventMessage eventMessage = sampleEventMessage();
        when(clickEventRepository.existsByEventId(eventMessage.eventId())).thenReturn(true);

        clickEventConsumer.consume(eventMessage);

        verify(urlRepository, never()).findById(any());
        verify(clickEventRepository, never()).save(any());
    }

    @Test
    void shouldEnrichAndSaveClickEvent() {
        ClickEventMessage eventMessage = sampleEventMessage();
        Url url = new Url();
        url.setId(eventMessage.urlId());
        url.setShortCode(eventMessage.shortCode());

        when(clickEventRepository.existsByEventId(eventMessage.eventId())).thenReturn(false);
        when(urlRepository.findById(eventMessage.urlId())).thenReturn(Optional.of(url));
        when(userAgentParser.parse(eventMessage.userAgent())).thenReturn(
                new UserAgentParser.ParsedUserAgent("desktop", "Mac OS X", "Chrome")
        );
        when(geoLookupService.lookup(eventMessage.ipAddress())).thenReturn(
                new GeoLookupService.GeoLocation("CH", "Zurich")
        );
        when(clickEventRepository.save(any(ClickEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clickEventConsumer.consume(eventMessage);

        verify(clickEventRepository).save(any(ClickEvent.class));
        verify(clickEventRepository).save(argThat(clickEvent ->
                clickEvent.getUrl() == url
                        && clickEvent.getEventId().equals(eventMessage.eventId())
                        && clickEvent.getClickedAt().equals(eventMessage.clickedAt())
                        && "127.0.0.1".equals(clickEvent.getIpAddress())
                        && "CH".equals(clickEvent.getCountry())
                        && "Zurich".equals(clickEvent.getCity())
                        && "desktop".equals(clickEvent.getDeviceType())
                        && "Mac OS X".equals(clickEvent.getOs())
                        && "Chrome".equals(clickEvent.getBrowser())
                        && "https://example.com/ref".equals(clickEvent.getReferrer())
                        && "Mozilla/5.0".equals(clickEvent.getUserAgent())
                        && "trace-123".equals(clickEvent.getTraceId())
        ));
    }

    @Test
    void shouldThrowWhenUrlDoesNotExist() {
        ClickEventMessage eventMessage = sampleEventMessage();

        when(clickEventRepository.existsByEventId(eventMessage.eventId())).thenReturn(false);
        when(urlRepository.findById(eventMessage.urlId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clickEventConsumer.consume(eventMessage));
        verify(clickEventRepository, never()).save(any());
    }

    private ClickEventMessage sampleEventMessage() {
        return new ClickEventMessage(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "abc1234",
                Instant.parse("2026-03-31T12:00:00Z"),
                "127.0.0.1",
                "https://example.com/ref",
                "Mozilla/5.0",
                "trace-123"
        );
    }

    private static ClickEvent argThat(org.mockito.ArgumentMatcher<ClickEvent> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
