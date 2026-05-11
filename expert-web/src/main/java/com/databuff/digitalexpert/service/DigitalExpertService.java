package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AgentBindingGroupResponse;
import com.databuff.digitalexpert.dao.dto.BindExpertAgentsCommand;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.CreateManualExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ForwardTrainingSubmitResponse;
import com.databuff.digitalexpert.dao.dto.ImportExpertPackageResponse;
import com.databuff.digitalexpert.dao.dto.ManualCreateExpertResponse;
import com.databuff.digitalexpert.dao.dto.ManualUpdateExpertResponse;
import com.databuff.digitalexpert.dao.dto.SubmitForwardTrainingRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertCommand;
import com.databuff.digitalexpert.dao.dto.UpdateManualExpertRequest;
import com.databuff.digitalexpert.dao.enums.ExpertSource;
import com.databuff.digitalexpert.dao.enums.ExpertStatus;
import com.databuff.digitalexpert.dao.enums.ExpertStatusOperation;
import com.databuff.digitalexpert.dao.enums.ExpertType;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface DigitalExpertService {

    ExpertSummaryResponse createExpert(CreateExpertRequest request);

    ExpertSummaryResponse createExpert(CreateExpertRequest request, ExpertSource expertSource);

    List<ExpertSummaryResponse> listExpertsByNames(List<String> names,
                                                   ExpertType expertType,
                                                   List<String> appNames,
                                                   ExpertStatus status);

    List<AgentBindingGroupResponse> listAgentBindings();

    ManualCreateExpertResponse createManualExpert(CreateManualExpertRequest request, List<MultipartFile> skillFiles);

    ManualUpdateExpertResponse updateManualExpert(UpdateManualExpertRequest request, List<MultipartFile> skillFiles);

    ExpertBindingUpdateResponse bindAgents(BindExpertAgentsCommand request);

    ForwardTrainingSubmitResponse submitForwardTraining(SubmitForwardTrainingRequest request);

    ImportExpertPackageResponse importExpertPackage(MultipartFile packageFile, boolean autoRelease);

    ExpertSummaryResponse updateExpert(UpdateExpertCommand request);

    ExpertBindingUpdateResponse updateBindings(Long expertId, UpdateExpertBindingsRequest request);

    boolean deleteExpert(Long expertId);

    List<ExpertSummaryResponse> changeExpertStatus(List<Long> expertIds, ExpertStatusOperation operation);
}
