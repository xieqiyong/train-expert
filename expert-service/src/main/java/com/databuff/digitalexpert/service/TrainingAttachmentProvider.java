package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.bo.TrainingAttachmentContext;
import com.databuff.digitalexpert.dao.dto.TrainingAttachmentRef;
import java.util.List;

public interface TrainingAttachmentProvider {

    default boolean supports(TrainingAttachmentContext context) {
        return true;
    }

    List<TrainingAttachmentRef> resolve(TrainingAttachmentContext context);
}
