package com.databuff.digitalexpert.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReleaseExecutorConfig {

    @Bean(name = "releaseTaskExecutor")
    public Executor releaseTaskExecutor(DigitalExpertProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("expert-release-");
        executor.setCorePoolSize(properties.getReleaseExecutorPoolSize());
        executor.setMaxPoolSize(properties.getReleaseExecutorPoolSize());
        executor.setQueueCapacity(properties.getReleaseExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }
}

