package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record MetricsResourceGenerateRequest(
        List<String> type1s,
        String app,
        String database,
        Boolean includeDisabled
) {
}
