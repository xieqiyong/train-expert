package com.databuff.digitalexpert.facade.service;

import com.databuff.digitalexpert.dao.dto.ExpertBatchQueryRequest;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.facade.dto.ExpertMcpUpsertRequest;
import com.databuff.digitalexpert.facade.dto.ExpertMcpUpsertResponse;
import java.util.List;

public interface ExpertFacadeService {

    List<ExpertSummaryResponse> listExperts(ExpertBatchQueryRequest request);

    ExpertMcpUpsertResponse upsertMcp(ExpertMcpUpsertRequest request);
}
