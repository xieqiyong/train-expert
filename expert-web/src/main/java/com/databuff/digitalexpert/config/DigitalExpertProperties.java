package com.databuff.digitalexpert.config;

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
public class DigitalExpertProperties {

    @NotBlank
    private String sharedRoot = "./data/shared";

    @Min(1)
    private int releaseExecutorPoolSize = 2;

    @Min(1)
    private int releaseExecutorQueueCapacity = 50;
}

