package com.databuff.digitalexpert;

import com.databuff.digitalexpert.config.ExpertProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(ExpertProperties.class)
public class DigitalExpertApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalExpertApplication.class, args);
    }
}
