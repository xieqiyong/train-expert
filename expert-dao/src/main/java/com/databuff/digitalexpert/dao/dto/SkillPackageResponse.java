package com.databuff.digitalexpert.dao.dto;

public record SkillPackageResponse(
        Long id,
        String name,
        String description,
        String packageName,
        String packagePath,
        String checksum,
        String status
) {
}
