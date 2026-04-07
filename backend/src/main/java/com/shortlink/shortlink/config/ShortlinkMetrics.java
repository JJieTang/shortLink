package com.shortlink.shortlink.config;

public final class ShortlinkMetrics {

    public static final String REDIRECTS_TOTAL = "shortlink_redirects_total";
    public static final String REDIRECT_LATENCY = "shortlink_redirect_latency_seconds";
    public static final String URLS_CREATED_TOTAL = "shortlink_urls_created_total";
    public static final String CACHE_HITS_TOTAL = "shortlink_cache_hits_total";
    public static final String CACHE_MISSES_TOTAL = "shortlink_cache_misses_total";
    public static final String DROPPED_EVENTS_TOTAL = "shortlink_dropped_events_total";
    public static final String CLICK_PROCESSING_ERRORS_TOTAL = "shortlink_click_processing_errors_total";
    public static final String RATE_LIMITED_TOTAL = "shortlink_rate_limited_total";
    public static final String DLQ_SIZE = "shortlink_dlq_size";
    public static final String CONSUMER_LAG = "shortlink_consumer_lag";

    public static final String STATUS_TAG = "status";
    public static final String CACHE_RESULT_TAG = "cache_result";
    public static final String URL_TYPE_TAG = "url_type";
    public static final String SCOPE_TAG = "scope";

    public static final String CACHE_HIT = "hit";
    public static final String CACHE_MISS = "miss";
    public static final String CACHE_UNKNOWN = "unknown";
    public static final String URL_TYPE_CUSTOM = "custom";
    public static final String URL_TYPE_GENERATED = "generated";

    private ShortlinkMetrics() {
    }
}
