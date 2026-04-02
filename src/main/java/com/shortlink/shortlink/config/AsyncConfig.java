package com.shortlink.shortlink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "clickEventExecutor")
    public Executor clickEventExecutor(
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
        executor.initialize();
        return executor;
    }
}
