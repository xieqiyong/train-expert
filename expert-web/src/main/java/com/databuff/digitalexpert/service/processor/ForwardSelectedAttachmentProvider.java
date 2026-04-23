package com.databuff.digitalexpert.service.processor;

import com.databuff.digitalexpert.dao.bo.TrainingAttachmentContext;
import com.databuff.digitalexpert.dao.dto.TrainingAttachmentRef;
import com.databuff.digitalexpert.service.AttachmentResourceService;
import com.databuff.digitalexpert.service.TrainingAttachmentProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ForwardSelectedAttachmentProvider implements TrainingAttachmentProvider {

    @Autowired
    private AttachmentResourceService attachmentResourceService;

    @Override
    public boolean supports(TrainingAttachmentContext context) {
        return context != null
                && context.selectedAttachmentIds() != null
                && !context.selectedAttachmentIds().isEmpty();
    }

    @Override
    public List<TrainingAttachmentRef> resolve(TrainingAttachmentContext context) {
        return attachmentResourceService.requireActiveByIds(context.selectedAttachmentIds());
    }
}
