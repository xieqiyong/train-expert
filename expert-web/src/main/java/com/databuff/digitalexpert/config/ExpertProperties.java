package com.databuff.digitalexpert.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "digital-expert")
public class ExpertProperties {

    private String staticPackage;

    private Release release = new Release();

    private Training training = new Training();

    private Proxy proxy = new Proxy();

    private Cors cors = new Cors();

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

        @NotBlank
        private String outputRoot = Paths.get(System.getProperty("user.dir"), "data", "training-output")
                .toAbsolutePath()
                .normalize()
                .toString();

        private String specSkillPath;

        @Min(1)
        private int submitExecutorPoolSize = 5;

        @Min(1)
        private int submitExecutorQueueCapacity = 100;

        @Min(1)
        private int pollExecutorPoolSize = 4;

        @Min(1)
        private int pollExecutorQueueCapacity = 200;

        @Min(1000)
        private int pollIntervalMs = 5000;

        @Min(1000)
        private int sessionTimeoutMs = 60 * 60 * 1000;

        @Min(1000)
        private int artifactGracePeriodMs = 2 * 60 * 1000;

        @Min(1000)
        private int releaseTimeoutMs = 30 * 60 * 1000;
    }

    @Getter
    @Setter
    public static class Proxy {

        @NotBlank
        private String baseUrl = "http://192.168.50.18:19300";

        @Min(1000)
        private int timeoutMs = 15000;
    }

    @Getter
    @Setter
    public static class Cors {

        private boolean enabled = true;

        @NotBlank
        private String pathPattern = "/api/**";

        private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        private List<String> exposedHeaders = new ArrayList<>(List.of("Content-Disposition"));

        private boolean allowCredentials = false;

        @Min(0)
        private long maxAgeSeconds = 3600;
    }
}