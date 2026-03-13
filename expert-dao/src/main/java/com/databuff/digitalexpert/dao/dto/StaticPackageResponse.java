package com.databuff.digitalexpert.dao.dto;

public record StaticPackageResponse(
        Long id,
        String name,
        String description,
        String packageName,
        String packagePath,
        String checksum,
        String status
) {
}
