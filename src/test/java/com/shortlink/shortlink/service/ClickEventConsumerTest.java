package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.ClickEvent;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.repository.ClickEventBatchRepository;
import com.shortlink.shortlink.repository.ClickEventRepository;
import com.shortlink.shortlink.repository.UrlBatchRepository;
import com.shortlink.shortlink.repository.UrlDailyStatBatchRepository;
import com.shortlink.shortlink.repository.UrlDailyStatRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
        verify(clickEventRepository, never()).findExistingUniqueVisitors(any());
        verify(clickEventRepository, never()).save(any());
        verify(clickEventRepository, never()).saveAll(any());
        verify(urlDailyStatRepository, never()).upsertDailyCountsBatch(any());
        verify(urlRepository, never()).incrementTotalClicksBatch(any());
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
        when(clickEventRepository.findExistingUniqueVisitors(any())).thenReturn(List.of());
        when(userAgentParser.parse(eventMessage.userAgent())).thenReturn(
                new UserAgentParser.ParsedUserAgent("desktop", "Mac OS X", "Chrome")
        );
        when(geoLookupService.lookup(eventMessage.ipAddress())).thenReturn(
                new GeoLookupService.GeoLocation("CH", "Zurich")
        );

        clickEventConsumer.consume(eventMessage);

        ArgumentCaptor<Iterable<ClickEvent>> clickEventsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(clickEventRepository).saveAll(clickEventsCaptor.capture());
        List<ClickEvent> savedEvents = toList(clickEventsCaptor.getValue());
        assertEquals(1, savedEvents.size());
        ClickEvent savedEvent = savedEvents.getFirst();
        assertEquals(url, savedEvent.getUrl());
        assertEquals(eventMessage.eventId(), savedEvent.getEventId());
        assertEquals(eventMessage.clickedAt(), savedEvent.getClickedAt());
        assertEquals("127.0.0.1", savedEvent.getIpAddress());
        assertEquals("CH", savedEvent.getCountry());
        assertEquals("Zurich", savedEvent.getCity());
        assertEquals("desktop", savedEvent.getDeviceType());
        assertEquals("Mac OS X", savedEvent.getOs());
        assertEquals("Chrome", savedEvent.getBrowser());
        assertEquals("https://example.com/ref", savedEvent.getReferrer());
        assertEquals("Mozilla/5.0", savedEvent.getUserAgent());
        assertEquals("trace-123", savedEvent.getTraceId());
        verify(clickEventRepository, never()).save(any());
        verify(clickEventRepository, never()).save(argThat(clickEvent ->
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
        verify(urlDailyStatRepository).upsertDailyCountsBatch(argThatDailyCountUpdates(updates ->
                updates.size() == 1
                        && updates.getFirst().urlId().equals(eventMessage.urlId())
                        && updates.getFirst().statDate().equals(LocalDate.of(2026, 3, 31))
                        && updates.getFirst().clickCount() == 1
                        && updates.getFirst().uniqueCount() == 1
        ));
        verify(urlRepository).incrementTotalClicksBatch(argThatTotalClickUpdates(updates ->
                updates.size() == 1
                        && updates.getFirst().urlId().equals(eventMessage.urlId())
                        && updates.getFirst().delta() == 1
        ));
    }

    @Test
    void shouldNotIncrementUniqueCountWhenIpAlreadySeenThatDay() {
        ClickEventMessage eventMessage = sampleEventMessage();
        Url url = new Url();
        url.setId(eventMessage.urlId());

        when(clickEventRepository.findExistingEventIdsByEventIdIn(List.of(eventMessage.eventId())))
                .thenReturn(List.of());
        when(urlRepository.findAllById(any())).thenReturn(List.of(url));
        when(clickEventRepository.findExistingUniqueVisitors(any())).thenReturn(List.of(
                new ClickEventBatchRepository.ExistingUniqueVisitor(
                        eventMessage.urlId(),
                        LocalDate.of(2026, 3, 31),
                        eventMessage.ipAddress()
                )
        ));
        when(userAgentParser.parse(eventMessage.userAgent())).thenReturn(
                new UserAgentParser.ParsedUserAgent("desktop", "Mac OS X", "Chrome")
        );
        when(geoLookupService.lookup(eventMessage.ipAddress())).thenReturn(
                new GeoLookupService.GeoLocation("CH", "Zurich")
        );

        clickEventConsumer.consume(eventMessage);

        verify(urlDailyStatRepository).upsertDailyCountsBatch(argThatDailyCountUpdates(updates ->
                updates.size() == 1
                        && updates.getFirst().urlId().equals(eventMessage.urlId())
                        && updates.getFirst().statDate().equals(LocalDate.of(2026, 3, 31))
                        && updates.getFirst().clickCount() == 1
                        && updates.getFirst().uniqueCount() == 0
        ));
        verify(urlRepository).incrementTotalClicksBatch(argThatTotalClickUpdates(updates ->
                updates.size() == 1
                        && updates.getFirst().urlId().equals(eventMessage.urlId())
                        && updates.getFirst().delta() == 1
        ));
    }

    @Test
    void shouldThrowWhenUrlDoesNotExist() {
        ClickEventMessage eventMessage = sampleEventMessage();

        when(clickEventRepository.findExistingEventIdsByEventIdIn(List.of(eventMessage.eventId())))
                .thenReturn(List.of());
        when(clickEventRepository.findExistingUniqueVisitors(any())).thenReturn(List.of());
        when(urlRepository.findAllById(any())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> clickEventConsumer.consume(eventMessage));
        verify(clickEventRepository, never()).save(any());
        verify(clickEventRepository, never()).saveAll(any());
        verify(urlDailyStatRepository, never()).upsertDailyCountsBatch(any());
        verify(urlRepository, never()).incrementTotalClicksBatch(any());
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
        when(clickEventRepository.findExistingUniqueVisitors(any())).thenReturn(List.of());
        when(userAgentParser.parse(any())).thenReturn(
                new UserAgentParser.ParsedUserAgent("desktop", "Mac OS X", "Chrome")
        );
        when(geoLookupService.lookup(any())).thenReturn(
                new GeoLookupService.GeoLocation("CH", "Zurich")
        );

        clickEventConsumer.consumeBatch(List.of(first, duplicate, second));

        verify(clickEventRepository).findExistingEventIdsByEventIdIn(List.of(first.eventId(), second.eventId()));
        verify(clickEventRepository).findExistingUniqueVisitors(any());
        verify(urlRepository).findAllById(any());
        ArgumentCaptor<Iterable<ClickEvent>> clickEventsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(clickEventRepository).saveAll(clickEventsCaptor.capture());
        assertEquals(2, toList(clickEventsCaptor.getValue()).size());
        verify(clickEventRepository, never()).save(any());
        verify(urlDailyStatRepository).upsertDailyCountsBatch(argThatDailyCountUpdates(updates -> updates.size() == 2));
        verify(urlRepository).incrementTotalClicksBatch(argThatTotalClickUpdates(updates ->
                updates.size() == 2
        ));
    }

    @Test
    void shouldAggregateDailyStatsAndTotalClicksForSameUrlWithinBatch() {
        ClickEventMessage first = sampleEventMessage();
        ClickEventMessage second = new ClickEventMessage(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440099"),
                first.urlId(),
                first.shortCode(),
                Instant.parse("2026-03-31T12:05:00Z"),
                "127.0.0.2",
                "https://example.com/ref-2",
                "Mozilla/5.0",
                "trace-456"
        );

        Url url = new Url();
        url.setId(first.urlId());

        when(clickEventRepository.findExistingEventIdsByEventIdIn(List.of(first.eventId(), second.eventId())))
                .thenReturn(List.of());
        when(urlRepository.findAllById(any())).thenReturn(List.of(url));
        when(clickEventRepository.findExistingUniqueVisitors(any())).thenReturn(List.of());
        when(userAgentParser.parse(any())).thenReturn(
                new UserAgentParser.ParsedUserAgent("desktop", "Mac OS X", "Chrome")
        );
        when(geoLookupService.lookup(any())).thenReturn(
                new GeoLookupService.GeoLocation("CH", "Zurich")
        );

        clickEventConsumer.consumeBatch(List.of(first, second));

        ArgumentCaptor<Iterable<ClickEvent>> clickEventsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(clickEventRepository).saveAll(clickEventsCaptor.capture());
        assertEquals(2, toList(clickEventsCaptor.getValue()).size());
        verify(urlDailyStatRepository).upsertDailyCountsBatch(argThatDailyCountUpdates(updates ->
                updates.size() == 1
                        && updates.getFirst().urlId().equals(first.urlId())
                        && updates.getFirst().statDate().equals(LocalDate.of(2026, 3, 31))
                        && updates.getFirst().clickCount() == 2
                        && updates.getFirst().uniqueCount() == 2
        ));
        verify(urlRepository).incrementTotalClicksBatch(argThatTotalClickUpdates(updates ->
                updates.size() == 1
                        && updates.getFirst().urlId().equals(first.urlId())
                        && updates.getFirst().delta() == 2
        ));
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

    private static List<UrlDailyStatBatchRepository.DailyCountUpdate> argThatDailyCountUpdates(
            org.mockito.ArgumentMatcher<List<UrlDailyStatBatchRepository.DailyCountUpdate>> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }

    private static List<UrlBatchRepository.TotalClickUpdate> argThatTotalClickUpdates(
            org.mockito.ArgumentMatcher<List<UrlBatchRepository.TotalClickUpdate>> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }

    private List<ClickEvent> toList(Iterable<ClickEvent> clickEvents) {
        List<ClickEvent> values = new ArrayList<>();
        clickEvents.forEach(values::add);
        return values;
    }
}
