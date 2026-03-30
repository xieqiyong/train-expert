package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AgentBindingGroupResponse;
import com.databuff.digitalexpert.dao.dto.CreateManualExpertRequest;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ManualCreateExpertResponse;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsRequest;
import com.databuff.digitalexpert.dao.enums.ExpertStatusOperation;
import com.databuff.digitalexpert.dao.enums.ExpertType;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface DigitalExpertService {

    ExpertSummaryResponse createExpert(CreateExpertRequest request);

    List<ExpertSummaryResponse> listExpertsByNames(List<String> names, ExpertType expertType);

    List<AgentBindingGroupResponse> listAgentBindings();

    ManualCreateExpertResponse createManualExpert(CreateManualExpertRequest request, List<MultipartFile> skillFiles);

    ExpertBindingUpdateResponse updateBindings(Long expertId, UpdateExpertBindingsRequest request);

    ExpertSummaryResponse changeExpertStatus(Long expertId, ExpertStatusOperation operation);
}
