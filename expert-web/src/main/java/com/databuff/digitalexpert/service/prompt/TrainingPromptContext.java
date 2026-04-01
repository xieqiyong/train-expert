package com.databuff.digitalexpert.service.prompt;

import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import java.nio.file.Path;
import java.util.List;

public record TrainingPromptContext(
        String trainingGoal,
        List<TrainingSourceRequest> sources,
        String skillDirName,
        Path skillRootDirectory,
        Path versionDirectory,
        Path specSkillPath,
        Path appInfoInputPath
) {

    public Path staticPackageDirectory() {
        return versionDirectory.resolve("static_package");
    }

    public boolean hasAppInfoSource() {
        return appInfoInputPath != null;
    }
}
