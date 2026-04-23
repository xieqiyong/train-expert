package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record MetricsCoreCategoryResponse(
        String type1,
        List<Type2Group> type2Groups
) {

    public record Type2Group(
            String type2,
            List<Type3Group> type3Groups
    ) {
    }

    public record Type3Group(
            String type3,
            Integer metricCount
    ) {
    }
}
