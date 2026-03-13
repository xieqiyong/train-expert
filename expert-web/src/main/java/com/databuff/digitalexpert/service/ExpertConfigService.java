package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.ExpertConfigResponse;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;

public interface ExpertConfigService {

    ExpertConfigResponse getConfig(Long expertId);

    void evict(Long expertId);

    DigitalExpertEntity requireExpert(Long expertId);
}
