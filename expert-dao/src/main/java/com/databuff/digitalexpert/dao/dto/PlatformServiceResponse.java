package com.databuff.digitalexpert.dao.dto;

public record PlatformServiceResponse(
        Long id,
        String name,
        String description,
        String serviceType
) {
}
