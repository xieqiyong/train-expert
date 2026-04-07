package com.databuff.digitalexpert.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ConversationSyncConfiguration {

    @Bean("conversationSyncExecutor")
    public Executor conversationSyncExecutor(ExpertProperties expertProperties) {
        ExpertProperties.Conversation conversation = expertProperties.getConversation();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("conversation-sync-");
        executor.setCorePoolSize(conversation.getExecutorPoolSize());
        executor.setMaxPoolSize(conversation.getExecutorPoolSize());
        executor.setQueueCapacity(conversation.getExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }
}
