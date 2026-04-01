package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.ClickEvent;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.repository.ClickEventRepository;
import com.shortlink.shortlink.repository.UrlDailyStatRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickEventConsumerTest {

    private ClickEventRepository clickEventRepository;
    private UrlDailyStatRepository urlDailyStatRepository;
    private UrlRepository urlRepository;
    private UserAgentParser userAgentParser;
    private GeoLookupService geoLookupService;
    private ClickEventConsumer clickEventConsumer;

    @BeforeEach
    void setUp() {
        clickEventRepository = mock(ClickEventRepository.class);
        urlDailyStatRepository = mock(UrlDailyStatRepository.class);
        urlRepository = mock(UrlRepository.class);
        userAgentParser = mock(UserAgentParser.class);
        geoLookupService = mock(GeoLookupService.class);

        clickEventConsumer = new ClickEventConsumer(
                clickEventRepository,
                urlDailyStatRepository,
                urlRepository,
                userAgentParser,
                geoLookupService
        );
    }

    @Test
    void shouldSkipDuplicateEvent() {
        ClickEventMessage eventMessage = sampleEventMessage();
        when(clickEventRepository.findExistingEventIdsByEventIdIn(List.of(eventMessage.eventId())))
                .thenReturn(List.of(eventMessage.eventId()));

        clickEventConsumer.consume(eventMessage);

        verify(urlRepository, never()).findAllById(any());
        verify(clickEventRepository, never()).save(any());
        verify(urlDailyStatRepository, never()).upsertDailyCounts(any(), any(), any(Long.class), any(Long.class));
        verify(urlRepository, never()).incrementTotalClicks(any(), any(Long.class));
    }

    @Test
    void shouldEnrichSaveAndIncrementUniqueCountForFirstIpOfDay() {
        ClickEventMessage eventMessage = sampleEventMessage();
        Url url = new Url();
        url.setId(eventMessage.urlId());
        url.setShortCode(eventMessage.shortCode());

        when(clickEventRepository.findExistingEventIdsByEventIdIn(List.of(eventMessage.eventId())))
                .thenReturn(List.of());
        when(urlRepository.findAllById(any())).thenReturn(List.of(url));
        when(clickEventRepository.existsByUrl_IdAndIpAddressAndClickedAtGreaterThanEqualAndClickedAtLessThan(
                eq(eventMessage.urlId()),
                eq(eventMessage.ipAddress()),
                eq(Instant.parse("2026-03-31T00:00:00Z")),
                eq(Instant.parse("2026-04-01T00:00:00Z"))
        )).thenReturn(false);
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
        verify(urlDailyStatRepository).upsertDailyCounts(
                eventMessage.urlId(),
                LocalDate.of(2026, 3, 31),
                1,
                1
        );
        verify(urlRepository).incrementTotalClicks(eventMessage.urlId(), 1);
    }

    @Test
    void shouldNotIncrementUniqueCountWhenIpAlreadySeenThatDay() {
        ClickEventMessage eventMessage = sampleEventMessage();
        Url url = new Url();
        url.setId(eventMessage.urlId());

        when(clickEventRepository.findExistingEventIdsByEventIdIn(List.of(eventMessage.eventId())))
                .thenReturn(List.of());
        when(urlRepository.findAllById(any())).thenReturn(List.of(url));
        when(clickEventRepository.existsByUrl_IdAndIpAddressAndClickedAtGreaterThanEqualAndClickedAtLessThan(
                eq(eventMessage.urlId()),
                eq(eventMessage.ipAddress()),
                eq(Instant.parse("2026-03-31T00:00:00Z")),
                eq(Instant.parse("2026-04-01T00:00:00Z"))
        )).thenReturn(true);
        when(userAgentParser.parse(eventMessage.userAgent())).thenReturn(
                new UserAgentParser.ParsedUserAgent("desktop", "Mac OS X", "Chrome")
        );
        when(geoLookupService.lookup(eventMessage.ipAddress())).thenReturn(
                new GeoLookupService.GeoLocation("CH", "Zurich")
        );

        clickEventConsumer.consume(eventMessage);

        verify(urlDailyStatRepository).upsertDailyCounts(
                eventMessage.urlId(),
                LocalDate.of(2026, 3, 31),
                1,
                0
        );
    }

    @Test
    void shouldThrowWhenUrlDoesNotExist() {
        ClickEventMessage eventMessage = sampleEventMessage();

        when(clickEventRepository.findExistingEventIdsByEventIdIn(List.of(eventMessage.eventId())))
                .thenReturn(List.of());
        when(urlRepository.findAllById(any())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> clickEventConsumer.consume(eventMessage));
        verify(clickEventRepository, never()).save(any());
        verify(urlDailyStatRepository, never()).upsertDailyCounts(any(), any(), any(Long.class), any(Long.class));
        verify(urlRepository, never()).incrementTotalClicks(any(), any(Long.class));
    }

    @Test
    void shouldDeduplicateBatchAndFetchUrlsOnce() {
        ClickEventMessage first = sampleEventMessage();
        ClickEventMessage duplicate = new ClickEventMessage(
                first.eventId(),
                first.urlId(),
                first.shortCode(),
                first.clickedAt(),
                first.ipAddress(),
                first.referrer(),
                first.userAgent(),
                first.traceId()
        );
        ClickEventMessage second = new ClickEventMessage(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440099"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                "xyz5678",
                Instant.parse("2026-03-31T12:05:00Z"),
                "127.0.0.2",
                "https://example.com/ref-2",
                "Mozilla/5.0",
                "trace-456"
        );

        Url firstUrl = new Url();
        firstUrl.setId(first.urlId());
        Url secondUrl = new Url();
        secondUrl.setId(second.urlId());

        when(clickEventRepository.findExistingEventIdsByEventIdIn(List.of(first.eventId(), second.eventId())))
                .thenReturn(List.of());
        when(urlRepository.findAllById(any())).thenReturn(List.of(firstUrl, secondUrl));
        when(clickEventRepository.existsByUrl_IdAndIpAddressAndClickedAtGreaterThanEqualAndClickedAtLessThan(
                eq(first.urlId()),
                eq(first.ipAddress()),
                eq(Instant.parse("2026-03-31T00:00:00Z")),
                eq(Instant.parse("2026-04-01T00:00:00Z"))
        )).thenReturn(false);
        when(clickEventRepository.existsByUrl_IdAndIpAddressAndClickedAtGreaterThanEqualAndClickedAtLessThan(
                eq(second.urlId()),
                eq(second.ipAddress()),
                eq(Instant.parse("2026-03-31T00:00:00Z")),
                eq(Instant.parse("2026-04-01T00:00:00Z"))
        )).thenReturn(false);
        when(userAgentParser.parse(any())).thenReturn(
                new UserAgentParser.ParsedUserAgent("desktop", "Mac OS X", "Chrome")
        );
        when(geoLookupService.lookup(any())).thenReturn(
                new GeoLookupService.GeoLocation("CH", "Zurich")
        );

        clickEventConsumer.consumeBatch(List.of(first, duplicate, second));

        verify(clickEventRepository).findExistingEventIdsByEventIdIn(List.of(first.eventId(), second.eventId()));
        verify(urlRepository).findAllById(any());
        verify(clickEventRepository, times(2)).save(any());
        verify(urlDailyStatRepository, times(2)).upsertDailyCounts(any(), any(), any(Long.class), any(Long.class));
        verify(urlRepository, times(2)).incrementTotalClicks(any(), any(Long.class));
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
