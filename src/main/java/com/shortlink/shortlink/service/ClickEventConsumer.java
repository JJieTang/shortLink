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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClickEventConsumer {

    private final ClickEventRepository clickEventRepository;
    private final UrlDailyStatRepository urlDailyStatRepository;
    private final UrlRepository urlRepository;
    private final UserAgentParser userAgentParser;
    private final GeoLookupService geoLookupService;

    public ClickEventConsumer(
            ClickEventRepository clickEventRepository,
            UrlDailyStatRepository urlDailyStatRepository,
            UrlRepository urlRepository,
            UserAgentParser userAgentParser,
            GeoLookupService geoLookupService) {
        this.clickEventRepository = clickEventRepository;
        this.urlDailyStatRepository = urlDailyStatRepository;
        this.urlRepository = urlRepository;
        this.userAgentParser = userAgentParser;
        this.geoLookupService = geoLookupService;
    }

    @Transactional
    public void consume(ClickEventMessage eventMessage) {
        consumeBatch(List.of(eventMessage));
    }

    @Transactional
    public void consumeBatch(List<ClickEventMessage> eventMessages) {
        if (eventMessages.isEmpty()) {
            return;
        }

        List<ClickEventMessage> uniqueMessages = deduplicateBatch(eventMessages);
        Set<UUID> existingEventIds = findExistingEventIds(uniqueMessages);
        List<ClickEventMessage> pendingMessages = uniqueMessages.stream()
                .filter(eventMessage -> !existingEventIds.contains(eventMessage.eventId()))
                .toList();

        if (pendingMessages.isEmpty()) {
            return;
        }

        Map<UUID, Url> urlsById = findUrlsById(pendingMessages);
        Set<UniqueVisitorKey> existingUniqueVisitors = findExistingUniqueVisitors(pendingMessages);
        Set<UniqueVisitorKey> countedUniqueVisitorsInBatch = new LinkedHashSet<>();
        List<ClickEvent> clickEvents = new ArrayList<>();
        Map<DailyStatKey, DailyCounts> dailyCountsByKey = new LinkedHashMap<>();
        Map<UUID, Long> totalClicksByUrlId = new LinkedHashMap<>();

        for (ClickEventMessage eventMessage : pendingMessages) {
            LocalDate statDate = resolveStatDate(eventMessage);
            long uniqueIncrement = resolveUniqueIncrement(
                    eventMessage,
                    statDate,
                    existingUniqueVisitors,
                    countedUniqueVisitorsInBatch
            );
            Url url = requireUrl(urlsById, eventMessage.urlId());

            clickEvents.add(toClickEvent(eventMessage, url));
            dailyCountsByKey.merge(
                    new DailyStatKey(eventMessage.urlId(), statDate),
                    new DailyCounts(1L, uniqueIncrement),
                    (existing, incoming) -> new DailyCounts(
                            existing.clickCount() + incoming.clickCount(),
                            existing.uniqueCount() + incoming.uniqueCount()
                    )
            );
            totalClicksByUrlId.merge(eventMessage.urlId(), 1L, Long::sum);
        }

        clickEventRepository.saveAll(clickEvents);
        List<UrlDailyStatBatchRepository.DailyCountUpdate> dailyCountUpdates = dailyCountsByKey.entrySet().stream()
                .map(entry -> new UrlDailyStatBatchRepository.DailyCountUpdate(
                        entry.getKey().urlId(),
                        entry.getKey().statDate(),
                        entry.getValue().clickCount(),
                        entry.getValue().uniqueCount()
                ))
                .toList();
        List<UrlBatchRepository.TotalClickUpdate> totalClickUpdates = totalClicksByUrlId.entrySet().stream()
                .map(entry -> new UrlBatchRepository.TotalClickUpdate(entry.getKey(), entry.getValue()))
                .toList();

        urlDailyStatRepository.upsertDailyCountsBatch(dailyCountUpdates);
        urlRepository.incrementTotalClicksBatch(totalClickUpdates);
    }

    private Url requireUrl(Map<UUID, Url> urlsById, UUID urlId) {
        Url url = urlsById.get(urlId);
        if (url == null) {
            throw new ResourceNotFoundException("URL not found for click event: " + urlId);
        }
        return url;
    }

    private ClickEvent toClickEvent(ClickEventMessage eventMessage, Url url) {
        UserAgentParser.ParsedUserAgent parsedUserAgent = userAgentParser.parse(eventMessage.userAgent());
        GeoLookupService.GeoLocation geoLocation = geoLookupService.lookup(eventMessage.ipAddress());

        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setUrl(url);
        clickEvent.setEventId(eventMessage.eventId());
        clickEvent.setClickedAt(eventMessage.clickedAt());
        clickEvent.setIpAddress(eventMessage.ipAddress());
        clickEvent.setCountry(geoLocation == null ? null : geoLocation.country());
        clickEvent.setCity(geoLocation == null ? null : geoLocation.city());
        clickEvent.setDeviceType(parsedUserAgent.deviceType());
        clickEvent.setOs(parsedUserAgent.os());
        clickEvent.setBrowser(parsedUserAgent.browser());
        clickEvent.setReferrer(eventMessage.referrer());
        clickEvent.setUserAgent(eventMessage.userAgent());
        clickEvent.setTraceId(eventMessage.traceId());

        return clickEvent;
    }

    private List<ClickEventMessage> deduplicateBatch(List<ClickEventMessage> eventMessages) {
        Map<UUID, ClickEventMessage> uniqueMessages = new LinkedHashMap<>();
        for (ClickEventMessage eventMessage : eventMessages) {
            uniqueMessages.putIfAbsent(eventMessage.eventId(), eventMessage);
        }
        return List.copyOf(uniqueMessages.values());
    }

    private Set<UUID> findExistingEventIds(Collection<ClickEventMessage> eventMessages) {
        List<UUID> eventIds = eventMessages.stream()
                .map(ClickEventMessage::eventId)
                .toList();
        return clickEventRepository.findExistingEventIdsByEventIdIn(eventIds).stream()
                .collect(Collectors.toSet());
    }

    private Map<UUID, Url> findUrlsById(Collection<ClickEventMessage> eventMessages) {
        Set<UUID> urlIds = eventMessages.stream()
                .map(ClickEventMessage::urlId)
                .collect(Collectors.toSet());

        return urlRepository.findAllById(urlIds).stream()
                .collect(Collectors.toMap(Url::getId, url -> url));
    }

    private Set<UniqueVisitorKey> findExistingUniqueVisitors(Collection<ClickEventMessage> eventMessages) {
        Map<UniqueVisitorKey, ClickEventBatchRepository.UniqueVisitorCandidate> candidatesByKey = new LinkedHashMap<>();

        for (ClickEventMessage eventMessage : eventMessages) {
            String ipAddress = eventMessage.ipAddress();
            if (ipAddress == null || ipAddress.isBlank()) {
                continue;
            }

            LocalDate statDate = resolveStatDate(eventMessage);
            Instant startInclusive = statDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant endExclusive = statDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            UniqueVisitorKey uniqueVisitorKey = new UniqueVisitorKey(eventMessage.urlId(), statDate, ipAddress);

            candidatesByKey.putIfAbsent(
                    uniqueVisitorKey,
                    new ClickEventBatchRepository.UniqueVisitorCandidate(
                            eventMessage.urlId(),
                            statDate,
                            ipAddress,
                            startInclusive,
                            endExclusive
                    )
            );
        }

        return clickEventRepository.findExistingUniqueVisitors(List.copyOf(candidatesByKey.values())).stream()
                .map(existingVisitor -> new UniqueVisitorKey(
                        existingVisitor.urlId(),
                        existingVisitor.statDate(),
                        existingVisitor.ipAddress()
                ))
                .collect(Collectors.toSet());
    }

    private LocalDate resolveStatDate(ClickEventMessage eventMessage) {
        return LocalDate.ofInstant(eventMessage.clickedAt(), ZoneOffset.UTC);
    }

    private long resolveUniqueIncrement(
            ClickEventMessage eventMessage,
            LocalDate statDate,
            Set<UniqueVisitorKey> existingUniqueVisitors,
            Set<UniqueVisitorKey> countedUniqueVisitorsInBatch) {
        String ipAddress = eventMessage.ipAddress();
        if (ipAddress == null || ipAddress.isBlank()) {
            return 0;
        }

        UniqueVisitorKey uniqueVisitorKey = new UniqueVisitorKey(eventMessage.urlId(), statDate, ipAddress);
        if (existingUniqueVisitors.contains(uniqueVisitorKey)) {
            return 0;
        }

        return countedUniqueVisitorsInBatch.add(uniqueVisitorKey) ? 1 : 0;
    }

    private record DailyStatKey(UUID urlId, LocalDate statDate) {
    }

    private record DailyCounts(long clickCount, long uniqueCount) {
    }

    private record UniqueVisitorKey(UUID urlId, LocalDate statDate, String ipAddress) {
    }
}
