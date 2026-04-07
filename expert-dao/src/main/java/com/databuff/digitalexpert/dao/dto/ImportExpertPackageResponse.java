package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record ImportExpertPackageResponse(
        ExpertSummaryResponse expert,
        List<SkillPackageResponse> skills,
        List<StaticPackageResponse> staticPackages,
        ExpertReleaseTaskResponse releaseTask
) {
}
