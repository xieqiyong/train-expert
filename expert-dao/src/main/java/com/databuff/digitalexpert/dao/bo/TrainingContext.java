package com.databuff.digitalexpert.dao.bo;

import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.entity.ExpertTrainingTaskEntity;
import com.databuff.digitalexpert.dao.enums.TrainingTaskStatus;
import java.util.List;

public record TrainingContext(
        TrainingTaskStatus status,
        ExpertTrainingTaskEntity trainingTask,
        DigitalExpertEntity expert,
        List<Long> skillIds,
        List<Long> staticPackageIds
) {
}
