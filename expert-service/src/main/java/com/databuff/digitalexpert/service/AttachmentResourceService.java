package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AttachmentResourceResponse;
import com.databuff.digitalexpert.dao.dto.TrainingAttachmentRef;
import java.util.List;

public interface AttachmentResourceService {

    List<AttachmentResourceResponse> listResources(String scope, String status);

    List<TrainingAttachmentRef> requireActiveByIds(List<Long> ids);

    List<TrainingAttachmentRef> listReverseDefaultAttachments();
}
