package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.MetricsCoreCategoryResponse;
import com.databuff.digitalexpert.dao.dto.MetricsCoreQueryRequest;
import com.databuff.digitalexpert.dao.dto.MetricsCoreResponse;
import com.databuff.digitalexpert.dao.dto.MetricsResourceFileResponse;
import com.databuff.digitalexpert.dao.dto.MetricsResourceGenerateRequest;
import java.util.List;

public interface MetricsCoreService {

    List<MetricsCoreResponse> list(MetricsCoreQueryRequest request);

    List<MetricsCoreCategoryResponse> listCategories(MetricsCoreQueryRequest request);

    List<MetricsResourceFileResponse> generateResourceFiles(MetricsResourceGenerateRequest request);
}
