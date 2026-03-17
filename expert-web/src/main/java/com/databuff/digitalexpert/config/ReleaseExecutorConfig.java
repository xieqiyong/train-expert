package com.databuff.digitalexpert.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReleaseExecutorConfig {

    @Bean(name = "releaseTaskExecutor")
    public Executor releaseTaskExecutor(ExpertProperties properties) {
        ExpertProperties.Release release = properties.getRelease();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("expert-release-");
        executor.setCorePoolSize(release.getExecutorPoolSize());
        executor.setMaxPoolSize(release.getExecutorPoolSize());
        executor.setQueueCapacity(release.getExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }
}
