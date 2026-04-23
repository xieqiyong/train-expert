package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record MetricsCoreQueryRequest(
        List<String> type1s,
        String type1,
        String type2,
        String type3,
        String app,
        String database,
        String measurement,
        Boolean includeDisabled,
        Integer limit
) {
}
