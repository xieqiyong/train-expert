package com.databuff.digitalexpert;

import com.databuff.digitalexpert.config.ExpertProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableKafka
@EnableCaching
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(ExpertProperties.class)
@MapperScan({"com.databuff.digitalexpert.dao.mapper"})
public class DigitalExpertApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalExpertApplication.class, args);
    }
}
