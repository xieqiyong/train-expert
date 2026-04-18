package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record ManualUpdateExpertResponse(
        ExpertSummaryResponse expert,
        List<SkillPackageResponse> skills,
        ExpertReleaseTaskResponse releaseTask
) {
}
