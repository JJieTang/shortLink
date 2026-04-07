package com.shortlink.shortlink.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class RedirectMetricsFilter extends OncePerRequestFilter {

    private static final Pattern REDIRECT_PATH_PATTERN = Pattern.compile("^/[A-Za-z0-9_-]{1,30}$");

    private final MeterRegistry meterRegistry;

    public RedirectMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isRedirectRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        String status = null;

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            status = Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw exception;
        } finally {
            recordRedirectMetrics(
                    status != null ? status : Integer.toString(response.getStatus()),
                    resolveCacheResult(request),
                    sample
            );
        }
    }

    private boolean isRedirectRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && REDIRECT_PATH_PATTERN.matcher(request.getRequestURI()).matches();
    }

    private String resolveCacheResult(HttpServletRequest request) {
        Object cacheResult = request.getAttribute(ShortlinkMetrics.REDIRECT_CACHE_RESULT_REQUEST_ATTRIBUTE);
        return cacheResult instanceof String value && !value.isBlank()
                ? value
                : ShortlinkMetrics.CACHE_UNKNOWN;
    }

    private void recordRedirectMetrics(String status, String cacheResult, Timer.Sample sample) {
        sample.stop(Timer.builder(ShortlinkMetrics.REDIRECT_LATENCY)
                .description("End-to-end latency of short-link redirect requests")
                .tags(
                        ShortlinkMetrics.STATUS_TAG, status,
                        ShortlinkMetrics.CACHE_RESULT_TAG, cacheResult
                )
                .register(meterRegistry));

        meterRegistry.counter(
                ShortlinkMetrics.REDIRECTS_TOTAL,
                ShortlinkMetrics.STATUS_TAG, status,
                ShortlinkMetrics.CACHE_RESULT_TAG, cacheResult
        ).increment();
    }
}
