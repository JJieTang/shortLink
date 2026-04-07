package com.shortlink.shortlink.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(name = "clickEventPublishExecutor")
    public Executor clickEventPublishExecutor(
            MeterRegistry meterRegistry,
            @Value("${app.async.click-event.core-pool-size:2}") int corePoolSize,
            @Value("${app.async.click-event.max-pool-size:4}") int maxPoolSize,
            @Value("${app.async.click-event.queue-capacity:200}") int queueCapacity,
            @Value("${app.async.click-event.thread-name-prefix:click-event-}") String threadNamePrefix,
            @Value("${app.async.click-event.await-termination-seconds:10}") int awaitTerminationSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        // Under overload we prefer preserving redirect latency over analytics completeness.
        executor.setRejectedExecutionHandler((task, rejectedExecutor) ->
                meterRegistry.counter(ShortlinkMetrics.DROPPED_EVENTS_TOTAL).increment()
        );
        executor.initialize();
        return executor;
    }

    @Bean(name = "clickEventWorkerExecutor", destroyMethod = "shutdown")
    public ExecutorService clickEventWorkerExecutor(
            @Value("${app.async.click-event.worker-thread-name-prefix:click-event-worker-}") String threadNamePrefix) {
        return Executors.newSingleThreadExecutor(new CustomizableThreadFactory(threadNamePrefix));
    }
}
