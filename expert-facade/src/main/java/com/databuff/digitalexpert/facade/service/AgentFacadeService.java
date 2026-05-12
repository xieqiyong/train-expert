package com.databuff.digitalexpert.facade.service;

import com.databuff.digitalexpert.dao.dto.AgentBatchQueryRequest;
import com.databuff.digitalexpert.dao.dto.AgentSummaryResponse;
import com.databuff.digitalexpert.dao.dto.PlatformServiceResponse;
import com.databuff.digitalexpert.facade.dto.AgentMcpDeployRequest;
import com.databuff.digitalexpert.facade.dto.AgentMcpDeployResponse;
import com.databuff.digitalexpert.facade.dto.AgentOpencodeRefreshRequest;
import com.databuff.digitalexpert.facade.dto.AgentOpencodeRefreshResponse;
import java.util.List;

public interface AgentFacadeService {

    List<AgentSummaryResponse> listAgents(AgentBatchQueryRequest request);

    List<PlatformServiceResponse> listPlatformServices();

    AgentOpencodeRefreshResponse refreshOpencode(AgentOpencodeRefreshRequest request);

    AgentMcpDeployResponse deployMcpsToOpencode(AgentMcpDeployRequest request);
}
