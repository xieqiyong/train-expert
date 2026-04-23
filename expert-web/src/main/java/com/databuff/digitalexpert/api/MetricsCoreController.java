package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.MetricsCoreCategoryResponse;
import com.databuff.digitalexpert.dao.dto.MetricsCoreQueryRequest;
import com.databuff.digitalexpert.dao.dto.MetricsCoreResponse;
import com.databuff.digitalexpert.dao.dto.MetricsResourceFileResponse;
import com.databuff.digitalexpert.dao.dto.MetricsResourceGenerateRequest;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.MetricsCoreService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics-core")
public class MetricsCoreController {

    @Autowired
    private MetricsCoreService metricsCoreService;

    @PostMapping("/list")
    public ApiResponse<List<MetricsCoreResponse>> list(@RequestBody(required = false) MetricsCoreQueryRequest request) {
        return ApiResponse.success(metricsCoreService.list(request));
    }

    @PostMapping("/categories")
    public ApiResponse<List<MetricsCoreCategoryResponse>> categories(@RequestBody(required = false) MetricsCoreQueryRequest request) {
        return ApiResponse.success(metricsCoreService.listCategories(request));
    }

    @PostMapping("/resources/generate")
    public ApiResponse<List<MetricsResourceFileResponse>> generateResources(
            @RequestBody(required = false) MetricsResourceGenerateRequest request) {
        return ApiResponse.success(metricsCoreService.generateResourceFiles(request));
    }
}
