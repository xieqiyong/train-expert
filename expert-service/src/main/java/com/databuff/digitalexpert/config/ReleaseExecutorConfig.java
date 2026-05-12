package com.databuff.digitalexpert.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReleaseExecutorConfig {

    @Bean(name = "releaseTaskExecutor")
    public Executor releaseTaskExecutor(ExpertProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("expert-release-");
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(1000);
        executor.initialize();
        return executor;
    }
}
