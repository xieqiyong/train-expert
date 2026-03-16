package com.databuff.digitalexpert.dao.dto;

public record ExpertConfigStaticPackageResponse(
        Long staticPackageId,
        String name,
        String staticType,
        String description,
        String packageName,
        String packagePath
) {
}
