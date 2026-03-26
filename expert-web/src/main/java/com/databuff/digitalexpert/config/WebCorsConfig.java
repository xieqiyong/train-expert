package com.databuff.digitalexpert.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Autowired
    private ExpertProperties expertProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        ExpertProperties.Cors cors = expertProperties.getCors();
        if (cors == null || !cors.isEnabled()) {
            return;
        }

        var registration = registry.addMapping(cors.getPathPattern())
                .allowedOriginPatterns(toArray(cors.getAllowedOriginPatterns(), "*"))
                .allowedMethods(toArray(cors.getAllowedMethods(), "GET", "POST", "PUT", "DELETE", "OPTIONS"))
                .allowedHeaders(toArray(cors.getAllowedHeaders(), "*"))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(cors.getMaxAgeSeconds());

        String[] exposedHeaders = toArray(cors.getExposedHeaders());
        if (exposedHeaders.length > 0) {
            registration.exposedHeaders(exposedHeaders);
        }
    }

    private String[] toArray(List<String> values, String... defaults) {
        if (CollectionUtils.isEmpty(values)) {
            return defaults;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
    }
}
