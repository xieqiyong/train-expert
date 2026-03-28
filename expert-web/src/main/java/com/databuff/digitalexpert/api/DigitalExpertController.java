package com.databuff.digitalexpert.api;

import com.alibaba.fastjson2.JSON;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.ChangeExpertStatusRequest;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.CreateManualExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBatchQueryRequest;
import com.databuff.digitalexpert.dao.dto.ExpertBindingUpdateResponse;
import com.databuff.digitalexpert.dao.dto.ExpertConfigResponse;
import com.databuff.digitalexpert.dao.dto.ExpertIdRequest;
import com.databuff.digitalexpert.dao.dto.ExpertReleaseTaskResponse;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ExpertTaskListQueryRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.ForwardTrainingSubmitResponse;
import com.databuff.digitalexpert.dao.dto.ManualCreateExpertResponse;
import com.databuff.digitalexpert.dao.dto.McpBindingRequest;
import com.databuff.digitalexpert.dao.dto.SubmitExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.SubmitForwardTrainingRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsCommand;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.DigitalExpertService;
import com.databuff.digitalexpert.service.ExpertConfigService;
import com.databuff.digitalexpert.service.ExpertReleaseService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import com.databuff.digitalexpert.service.ForwardTrainingService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/experts")
public class DigitalExpertController {

    @Autowired
    private DigitalExpertService digitalExpertService;
    @Autowired
    private ExpertConfigService expertConfigService;
    @Autowired
    private ExpertReleaseService expertReleaseService;
    @Autowired
    private ExpertTrainingService expertTrainingService;
    @Autowired
    private ForwardTrainingService forwardTrainingService;

    @PostMapping
    public ApiResponse<ExpertSummaryResponse> createExpert(@Valid @RequestBody CreateExpertRequest request) {
        return ApiResponse.success(digitalExpertService.createExpert(request));
    }

    @PostMapping("/list")
    public ApiResponse<List<ExpertSummaryResponse>> listExperts(@Valid @RequestBody ExpertBatchQueryRequest request) {
        return ApiResponse.success(digitalExpertService.listExpertsByNames(request.names(), request.expertType()));
    }

    @PostMapping(value = "/manual/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ManualCreateExpertResponse> createManualExpert(@RequestParam("name") String name,
                                                                      @RequestParam(value = "description", required = false) String description,
                                                                      @RequestParam(value = "prompt", required = false) String prompt,
                                                                      @RequestParam(value = "expertType", required = false) String expertType,
                                                                      @RequestParam(value = "mcpsJson", required = false) String mcpsJson,
                                                                      @RequestParam(value = "autoRelease", defaultValue = "true") boolean autoRelease,
                                                                      @RequestPart("skillFiles") List<MultipartFile> skillFiles) {
        return ApiResponse.success(digitalExpertService.createManualExpert(
                new CreateManualExpertRequest(
                        name,
                        description,
                        prompt,
                        expertType,
                        parseMcpsJson(mcpsJson),
                        autoRelease
                ),
                skillFiles
        ));
    }

    @PostMapping("/bindings/update")
    public ApiResponse<ExpertBindingUpdateResponse> updateBindings(@Valid @RequestBody UpdateExpertBindingsCommand request) {
        return ApiResponse.success(digitalExpertService.updateBindings(request.expertId(), request.toBindingsRequest()));
    }

    @PostMapping("/delete")
    public ApiResponse<Void> deleteExpert(@Valid @RequestBody ExpertIdRequest request) {
        digitalExpertService.deleteExpert(request.expertId());
        return ApiResponse.success();
    }

    @PostMapping("/release-tasks/submit")
    public ApiResponse<ExpertReleaseTaskResponse> submitReleaseTask(@Valid @RequestBody ExpertIdRequest request) {
        return ApiResponse.success(expertReleaseService.submitReleaseTask(request.expertId()));
    }

    @PostMapping("/release-tasks/detail")
    public ApiResponse<ExpertReleaseTaskResponse> getReleaseTask(@Valid @RequestBody ExpertTaskRequest request) {
        return ApiResponse.success(expertReleaseService.getTask(request.expertId(), request.taskId()));
    }

    @PostMapping("/release-tasks/list")
    public ApiResponse<List<ExpertReleaseTaskResponse>> listReleaseTasks(@RequestBody(required = false) ExpertTaskListQueryRequest request) {
        return ApiResponse.success(expertReleaseService.listTasks(request == null ? null : request.expertId()));
    }

    @PostMapping("/training-tasks/submit")
    public ApiResponse<ExpertTrainingTaskResponse> submitTrainingTask(@Valid @RequestBody SubmitExpertTrainingTaskRequest request) {
        return ApiResponse.success(expertTrainingService.submitTrainingTask(request.expertId(), request.toTrainingTaskRequest()));
    }

    @PostMapping(value = "/training-tasks/forward/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ForwardTrainingSubmitResponse> submitForwardTraining(@RequestParam("name") String name,
                                                                            @RequestParam(value = "description", required = false) String description,
                                                                            @RequestParam(value = "prompt", required = false) String prompt,
                                                                            @RequestParam(value = "expertType", required = false) String expertType,
                                                                            @RequestParam("sourceType") String sourceType,
                                                                            @RequestParam(value = "sourceValue", required = false) String sourceValue,
                                                                            @RequestParam(value = "sourceVersion", required = false) String sourceVersion,
                                                                            @RequestParam(value = "trainingGoal", required = false) String trainingGoal,
                                                                            @RequestPart(value = "docPackageFile", required = false) MultipartFile docPackageFile) {
        return ApiResponse.success(forwardTrainingService.submit(
                new SubmitForwardTrainingRequest(
                        name,
                        description,
                        prompt,
                        expertType,
                        sourceType,
                        sourceValue,
                        sourceVersion,
                        trainingGoal
                ),
                docPackageFile
        ));
    }

    @PostMapping("/training-tasks/detail")
    public ApiResponse<ExpertTrainingTaskResponse> getTrainingTask(@Valid @RequestBody ExpertTaskRequest request) {
        return ApiResponse.success(expertTrainingService.getTask(request.expertId(), request.taskId()));
    }

    @PostMapping("/training-tasks/list")
    public ApiResponse<List<ExpertTrainingTaskResponse>> listTrainingTasks(@RequestBody(required = false) ExpertTaskListQueryRequest request) {
        return ApiResponse.success(expertTrainingService.listTasks(request == null ? null : request.expertId()));
    }

    @PostMapping("/config/detail")
    public ApiResponse<ExpertConfigResponse> getConfig(@Valid @RequestBody ExpertIdRequest request) {
        return ApiResponse.success(expertConfigService.getConfig(request.expertId()));
    }

    @PostMapping("/package/download")
    public ResponseEntity<FileSystemResource> downloadPackage(@Valid @RequestBody ExpertIdRequest request) {
        ExpertConfigResponse config = expertConfigService.getConfig(request.expertId());
        if (config.zipPackagePath() == null || config.zipPackagePath().isBlank()) {
            throw BusinessException.badRequest(ErrorCode.EXPERT_PACKAGE_NOT_READY, "专家压缩包尚未生成");
        }
        Path zipPath = Path.of(config.zipPackagePath());
        if (!Files.exists(zipPath)) {
            throw BusinessException.badRequest(ErrorCode.EXPERT_PACKAGE_NOT_READY, "专家压缩包文件不存在");
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
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "输出专家压缩包失败");
        }
    }

    @PostMapping("/status/change")
    public ApiResponse<ExpertSummaryResponse> changeStatus(@Valid @RequestBody ChangeExpertStatusRequest request) {
        return ApiResponse.success(digitalExpertService.changeExpertStatus(request.expertId(), request.operation()));
    }

    private List<McpBindingRequest> parseMcpsJson(String mcpsJson) {
        if (mcpsJson == null || mcpsJson.isBlank()) {
            return List.of();
        }
        try {
            List<McpBindingRequest> result = JSON.parseArray(mcpsJson, McpBindingRequest.class);
            return result == null ? List.of() : result;
        } catch (Exception ex) {
            throw BusinessException.badRequest(ErrorCode.MCP_BINDING_INVALID, "MCP 配置 JSON 格式不正确");
        }
    }
}
