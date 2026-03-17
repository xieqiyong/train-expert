package com.databuff.digitalexpert.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TrainingExecutorConfig {

    @Bean(name = "trainingSubmitExecutor")
    public Executor trainingSubmitExecutor(ExpertProperties properties) {
        ExpertProperties.Training training = properties.getTraining();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("expert-train-submit-");
        executor.setCorePoolSize(training.getSubmitExecutorPoolSize());
        executor.setMaxPoolSize(training.getSubmitExecutorPoolSize());
        executor.setQueueCapacity(training.getSubmitExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }

    @Bean(name = "trainingPollExecutor")
    public Executor trainingPollExecutor(ExpertProperties properties) {
        ExpertProperties.Training training = properties.getTraining();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("expert-train-poll-");
        executor.setCorePoolSize(training.getPollExecutorPoolSize());
        executor.setMaxPoolSize(training.getPollExecutorPoolSize());
        executor.setQueueCapacity(training.getPollExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }
}
