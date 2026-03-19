package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.CreateManualExpertRequest;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ManualCreateExpertResponse;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsRequest;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface DigitalExpertService {

    ExpertSummaryResponse createExpert(CreateExpertRequest request);

    ManualCreateExpertResponse createManualExpert(CreateManualExpertRequest request, List<MultipartFile> skillFiles);

    ExpertBindingUpdateResponse updateBindings(Long expertId, UpdateExpertBindingsRequest request);

    ExpertSummaryResponse disableExpert(Long expertId);
}
