package com.databuff.digitalexpert.dao.dto;

import com.databuff.digitalexpert.dao.enums.AgentStatus;
import java.util.List;

public record AgentBatchQueryRequest(
        List<String> names,
        AgentStatus status
) {
}
