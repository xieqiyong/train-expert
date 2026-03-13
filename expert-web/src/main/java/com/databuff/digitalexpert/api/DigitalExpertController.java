package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigResponse;
import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsRequest;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.DigitalExpertService;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.ExpertReleaseService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/experts")
public class DigitalExpertController {

    private final DigitalExpertService digitalExpertService;
    private final ExpertConfigService expertConfigService;
    private final ExpertReleaseService expertReleaseService;

    @PostMapping
    public ApiResponse<ExpertSummaryResponse> createExpert(@Valid @RequestBody CreateExpertRequest request) {
        return ApiResponse.success(digitalExpertService.createExpert(request));
    }

    @PutMapping("/{expertId}/bindings")
    public ApiResponse<ExpertBindingUpdateResponse> updateBindings(@PathVariable Long expertId,
                                                                   @Valid @RequestBody UpdateExpertBindingsRequest request) {
        return ApiResponse.success(digitalExpertService.updateBindings(expertId, request));
    }

    @PostMapping("/{expertId}/release-tasks")
    public ApiResponse<ExpertReleaseTaskResponse> submitReleaseTask(@PathVariable Long expertId) {
        return ApiResponse.success(expertReleaseService.submitReleaseTask(expertId));
    }

    @GetMapping("/{expertId}/release-tasks/{taskId}")
    public ApiResponse<ExpertReleaseTaskResponse> getReleaseTask(@PathVariable Long expertId,
                                                                 @PathVariable String taskId) {
        return ApiResponse.success(expertReleaseService.getTask(expertId, taskId));
    }

    @GetMapping("/{expertId}/config")
    public ApiResponse<ExpertConfigResponse> getConfig(@PathVariable Long expertId) {
        return ApiResponse.success(expertConfigService.getConfig(expertId));
    }

    @GetMapping("/{expertId}/package/download")
    public ResponseEntity<FileSystemResource> downloadPackage(@PathVariable Long expertId) {
        ExpertConfigResponse config = expertConfigService.getConfig(expertId);
        if (config.zipPackagePath() == null || config.zipPackagePath().isBlank()) {
            throw BusinessException.badRequest(ErrorCode.EXPERT_PACKAGE_NOT_READY, "Expert package not ready");
        }
        Path zipPath = Path.of(config.zipPackagePath());
        if (!Files.exists(zipPath)) {
            throw BusinessException.badRequest(ErrorCode.EXPERT_PACKAGE_NOT_READY, "Expert package file not found");
        }
        FileSystemResource resource = new FileSystemResource(zipPath);
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(zipPath.getFileName().toString())
                            .build().toString())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "Failed to stream package");
        }
    }

    @PostMapping("/{expertId}/disable")
    public ApiResponse<ExpertSummaryResponse> disable(@PathVariable Long expertId) {
        return ApiResponse.success(digitalExpertService.disableExpert(expertId));
    }
}
