package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsRequest;

public interface DigitalExpertService {

    ExpertSummaryResponse createExpert(CreateExpertRequest request);

    ExpertBindingUpdateResponse updateBindings(Long expertId, UpdateExpertBindingsRequest request);

    ExpertSummaryResponse disableExpert(Long expertId);
}
