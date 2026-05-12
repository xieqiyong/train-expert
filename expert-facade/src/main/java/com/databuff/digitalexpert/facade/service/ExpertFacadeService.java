package com.databuff.digitalexpert.facade.service;

import com.databuff.digitalexpert.dao.dto.AgentBindingGroupResponse;
import com.databuff.digitalexpert.dao.dto.BindExpertAgentsCommand;
import com.databuff.digitalexpert.dao.dto.ChangeExpertStatusRequest;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.CreateManualExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBatchQueryRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigResponse;
import com.databuff.digitalexpert.dao.dto.ExpertIdRequest;
import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ExpertTaskListQueryRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.ImportExpertPackageResponse;
import com.databuff.digitalexpert.dao.dto.ManualCreateExpertResponse;
import com.databuff.digitalexpert.dao.dto.ManualUpdateExpertResponse;
import com.databuff.digitalexpert.dao.dto.SubmitExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsCommand;
import com.databuff.digitalexpert.dao.dto.UpdateExpertCommand;
import com.databuff.digitalexpert.dao.dto.UpdateManualExpertRequest;
import com.databuff.digitalexpert.facade.dto.ExpertMcpUpsertRequest;
import com.databuff.digitalexpert.facade.dto.ExpertMcpUpsertResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ExpertFacadeService {

    ExpertSummaryResponse createExpert(CreateExpertRequest request);

    List<ExpertSummaryResponse> listExperts(ExpertBatchQueryRequest request);

    ExpertConfigResponse getExpertConfig(ExpertIdRequest request);

    List<ExpertSummaryResponse> changeExpertStatus(ChangeExpertStatusRequest request);

    ExpertBindingUpdateResponse updateBindings(UpdateExpertBindingsCommand request);

    ExpertReleaseTaskResponse submitReleaseTask(ExpertIdRequest request);

    ExpertReleaseTaskResponse getReleaseTask(ExpertTaskRequest request);

    List<ExpertReleaseTaskResponse> listReleaseTasks(ExpertTaskListQueryRequest request);

    ExpertTrainingTaskResponse submitTrainingTask(SubmitExpertTrainingTaskRequest request);

    ExpertTrainingTaskResponse getTrainingTask(ExpertTaskRequest request);

    Boolean abortTrainingTask(ExpertTaskRequest request);

    List<ExpertTrainingTaskResponse> listTrainingTasks(ExpertTaskListQueryRequest request);

    List<AgentBindingGroupResponse> listAgentBindings();

    ExpertBindingUpdateResponse bindAgents(BindExpertAgentsCommand request);

    ExpertSummaryResponse updateExpert(UpdateExpertCommand request);

    Boolean deleteExpert(ExpertIdRequest request);

    ImportExpertPackageResponse importPackage(MultipartFile file, boolean autoRelease);

    ManualCreateExpertResponse createManualExpert(CreateManualExpertRequest request, List<MultipartFile> skillFiles);

    ManualUpdateExpertResponse updateManualExpert(UpdateManualExpertRequest request, List<MultipartFile> skillFiles);

    ExpertMcpUpsertResponse upsertMcp(ExpertMcpUpsertRequest request);
}
