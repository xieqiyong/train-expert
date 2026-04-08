package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.AgentBatchQueryRequest;
import com.databuff.digitalexpert.dao.dto.AgentBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.AgentConfigResponse;
import com.databuff.digitalexpert.dao.dto.AgentIdRequest;
import com.databuff.digitalexpert.dao.dto.AgentSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ChangeAgentStatusRequest;
import com.databuff.digitalexpert.dao.dto.CreateAgentRequest;
import com.databuff.digitalexpert.dao.dto.PlatformServiceResponse;
import com.databuff.digitalexpert.dao.dto.UpdateAgentBindingsCommand;
import com.databuff.digitalexpert.dao.dto.UpdateAgentExpertsCommand;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.AiAgentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/agents")
public class AiAgentController {

    @Autowired
    private AiAgentService aiAgentService;

    @PostMapping
    public ApiResponse<AgentSummaryResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.success(aiAgentService.createAgent(request));
    }

    @PostMapping("/list")
    public ApiResponse<List<AgentSummaryResponse>> listAgents(
            @RequestBody(required = false) AgentBatchQueryRequest request) {
        return ApiResponse.success(aiAgentService.listAgentsByNames(
                request == null ? null : request.names(),
                request == null ? null : request.status()
        ));
    }

    @PostMapping("/detail")
    public ApiResponse<AgentConfigResponse> getConfig(@Valid @RequestBody AgentIdRequest request) {
        return ApiResponse.success(aiAgentService.getConfig(request.agentId()));
    }

    @PostMapping("/services/list")
    public ApiResponse<List<PlatformServiceResponse>> listPlatformServices() {
        return ApiResponse.success(aiAgentService.listPlatformServices());
    }

    @PostMapping("/bindings/update")
    public ApiResponse<AgentBindingUpdateResponse> updateBindings(
            @Valid @RequestBody UpdateAgentBindingsCommand request) {
        return ApiResponse.success(aiAgentService.updateBindings(request.agentId(), request.toBindingsRequest()));
    }

    @PostMapping("/expert-bindings/update")
    public ApiResponse<AgentBindingUpdateResponse> updateExpertBindings(
            @Valid @RequestBody UpdateAgentExpertsCommand request) {
        return ApiResponse.success(aiAgentService.updateExpertBindings(request.agentId(), request.expertIds()));
    }

    @PostMapping("/status/change")
    public ApiResponse<List<AgentSummaryResponse>> changeStatus(
            @Valid @RequestBody ChangeAgentStatusRequest request) {
        return ApiResponse.success(aiAgentService.changeStatus(request.agentIds(), request.operation()));
    }
}
