package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.dto.CreateUrlRequest;
import com.shortlink.shortlink.dto.UrlResponse;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.service.UrlShorteningService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlShorteningService urlShorteningService;

    public UrlController(UrlShorteningService urlShorteningService) {
        this.urlShorteningService = urlShorteningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UrlResponse createUrl(@Valid @RequestBody CreateUrlRequest request) {
        Url url = urlShorteningService.createShortUrl(request);
        return UrlResponse.from(url, urlShorteningService.getBaseUrl());
    }

    @GetMapping("/{shortCode}")
    public UrlResponse getUrl(@PathVariable String shortCode) {
        Url url = urlShorteningService.getUrl(shortCode);
        return UrlResponse.from(url, urlShorteningService.getBaseUrl());
    }

    @DeleteMapping("/{shortCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUrl(@PathVariable String shortCode) {
        urlShorteningService.deleteUrl(shortCode);
    }

    @GetMapping
    public Page<UrlResponse> listUrls(
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
            ) {
        return urlShorteningService.listUrls(pageable)
                .map(url -> UrlResponse.from(url, urlShorteningService.getBaseUrl()));
    }
}
