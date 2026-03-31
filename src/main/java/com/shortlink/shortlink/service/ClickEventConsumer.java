package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.ClickEvent;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.repository.ClickEventRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClickEventConsumer {

    private final ClickEventRepository clickEventRepository;
    private final UrlRepository urlRepository;
    private final UserAgentParser userAgentParser;
    private final GeoLookupService geoLookupService;

    public ClickEventConsumer(
            ClickEventRepository clickEventRepository,
            UrlRepository urlRepository,
            UserAgentParser userAgentParser,
            GeoLookupService geoLookupService) {
        this.clickEventRepository = clickEventRepository;
        this.urlRepository = urlRepository;
        this.userAgentParser = userAgentParser;
        this.geoLookupService = geoLookupService;
    }

    @Transactional
    public void consume(ClickEventMessage eventMessage) {
        if (clickEventRepository.existsByEventId(eventMessage.eventId())) {
            return;
        }

        Url url = urlRepository.findById(eventMessage.urlId()).orElseThrow(
                () -> new ResourceNotFoundException("URL not found for click event: " + eventMessage.urlId())
        );

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

        clickEventRepository.save(clickEvent);
    }
}
