package com.databuff.digitalexpert;

import com.databuff.digitalexpert.config.DigitalExpertProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
@EnableConfigurationProperties(DigitalExpertProperties.class)
@MapperScan({"com.databuff.digitalexpert.dao.mapper"})
public class DigitalExpertApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalExpertApplication.class, args);
    }
}

