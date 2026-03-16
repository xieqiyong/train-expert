package com.databuff.digitalexpert.dao.dto;

public record StaticPackageResponse(
        Long id,
        String name,
        String staticType,
        String description,
        String packageName,
        String packagePath,
        String checksum,
        String status
) {
}
