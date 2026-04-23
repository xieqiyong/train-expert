package com.databuff.digitalexpert.dao.dto;

public record MetricsCoreResponse(
        Integer id,
        String type1,
        String type2,
        String type3,
        String app,
        String database,
        String measurement,
        String description,
        String tagKey,
        String tagValue,
        String fields,
        Integer isOpen,
        String metricType,
        String metricSource,
        Integer builtin
) {
}
