package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record ExpertConfigResponse(
        Long expertId,
        String name,
        String description,
        String prompt,
        String status,
        String sharedPath,
        String configJsonPath,
        String zipPackagePath,
        Integer releaseVersion,
        String lastReleaseTaskId,
        String lastReleaseTaskStatus,
        List<ExpertConfigSkillResponse> skills,
        List<ExpertConfigStaticPackageResponse> staticPackages,
        List<ExpertConfigMcpResponse> mcps
) {
}
