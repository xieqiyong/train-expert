package com.databuff.digitalexpert.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.databuff.digitalexpert.dao.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
public class ExpertMapperScanConfiguration {
}
