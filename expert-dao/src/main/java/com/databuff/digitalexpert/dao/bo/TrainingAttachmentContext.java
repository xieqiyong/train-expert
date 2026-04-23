package com.databuff.digitalexpert.dao.bo;

import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.enums.TrainingAttachmentMode;
import java.util.List;

public record TrainingAttachmentContext(
        TrainingAttachmentMode mode,
        DigitalExpertEntity expert,
        List<TrainingSourceRequest> sources,
        List<Long> selectedAttachmentIds
) {
}
