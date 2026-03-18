package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigResponse;
import com.databuff.digitalexpert.dao.dto.ExpertIdRequest;
import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ExpertTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.SubmitExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsCommand;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.DigitalExpertService;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.ExpertReleaseService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final ExpertTrainingService expertTrainingService;

    @PostMapping
    public ApiResponse<ExpertSummaryResponse> createExpert(@Valid @RequestBody CreateExpertRequest request) {
        return ApiResponse.success(digitalExpertService.createExpert(request));
    }

    @PostMapping("/bindings/update")
    public ApiResponse<ExpertBindingUpdateResponse> updateBindings(@Valid @RequestBody UpdateExpertBindingsCommand request) {
        return ApiResponse.success(
                digitalExpertService.updateBindings(request.expertId(), request.toBindingsRequest())
        );
    }

    @PostMapping("/release-tasks/submit")
    public ApiResponse<ExpertReleaseTaskResponse> submitReleaseTask(@Valid @RequestBody ExpertIdRequest request) {
        return ApiResponse.success(expertReleaseService.submitReleaseTask(request.expertId()));
    }

    @PostMapping("/release-tasks/detail")
    public ApiResponse<ExpertReleaseTaskResponse> getReleaseTask(@Valid @RequestBody ExpertTaskRequest request) {
        return ApiResponse.success(expertReleaseService.getTask(request.expertId(), request.taskId()));
    }

    @PostMapping("/training-tasks/submit")
    public ApiResponse<ExpertTrainingTaskResponse> submitTrainingTask(@Valid @RequestBody SubmitExpertTrainingTaskRequest request) {
        return ApiResponse.success(
                expertTrainingService.submitTrainingTask(request.expertId(), request.toTrainingTaskRequest())
        );
    }

    @PostMapping("/training-tasks/detail")
    public ApiResponse<ExpertTrainingTaskResponse> getTrainingTask(@Valid @RequestBody ExpertTaskRequest request) {
        return ApiResponse.success(expertTrainingService.getTask(request.expertId(), request.taskId()));
    }

    @PostMapping("/config/detail")
    public ApiResponse<ExpertConfigResponse> getConfig(@Valid @RequestBody ExpertIdRequest request) {
        return ApiResponse.success(expertConfigService.getConfig(request.expertId()));
    }

    @PostMapping("/package/download")
    public ResponseEntity<FileSystemResource> downloadPackage(@Valid @RequestBody ExpertIdRequest request) {
        ExpertConfigResponse config = expertConfigService.getConfig(request.expertId());
        if (config.zipPackagePath() == null || config.zipPackagePath().isBlank()) {
            throw BusinessException.badRequest(
                    ErrorCode.EXPERT_PACKAGE_NOT_READY,
                    "专家压缩包尚未生成"
            );
        }
        Path zipPath = Path.of(config.zipPackagePath());
        if (!Files.exists(zipPath)) {
            throw BusinessException.badRequest(
                    ErrorCode.EXPERT_PACKAGE_NOT_READY,
                    "专家压缩包文件不存在"
            );
        }

        FileSystemResource resource = new FileSystemResource(zipPath);
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(zipPath.getFileName().toString(), StandardCharsets.UTF_8)
                            .build().toString())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (IOException ex) {
            throw BusinessException.internal(
                    ErrorCode.INTERNAL_ERROR,
                    "输出专家压缩包失败"
            );
        }
    }

    @PostMapping("/disable")
    public ApiResponse<ExpertSummaryResponse> disable(@Valid @RequestBody ExpertIdRequest request) {
        return ApiResponse.success(digitalExpertService.disableExpert(request.expertId()));
    }
}
