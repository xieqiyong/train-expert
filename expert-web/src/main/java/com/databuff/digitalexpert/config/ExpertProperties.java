package com.databuff.digitalexpert.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "digital-expert")
public class ExpertProperties {

    private String sharedRoot = "./data/shared";

    private Release release = new Release();

    private Training training = new Training();

    private Proxy proxy = new Proxy();

    @Getter
    @Setter
    public static class Release {

        @Min(1)
        private int executorPoolSize = 2;

        @Min(1)
        private int executorQueueCapacity = 50;
    }

    @Getter
    @Setter
    public static class Training {

        @Min(1)
        private int submitExecutorPoolSize = 2;

        @Min(1)
        private int submitExecutorQueueCapacity = 100;

        @Min(1)
        private int pollExecutorPoolSize = 4;

        @Min(1)
        private int pollExecutorQueueCapacity = 200;

        @Min(1000)
        private int pollIntervalMs = 5000;

        @Min(1)
        private int maxPollCount = 720;
    }

    @Getter
    @Setter
    public static class Proxy {

        @NotBlank
        private String baseUrl = "http://192.168.50.18:19300";

        @Min(1000)
        private int timeoutMs = 15000;
    }
}
