package com.databuff.digitalexpert.api;

import com.alibaba.fastjson2.JSON;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.AgentBindingGroupResponse;
import com.databuff.digitalexpert.dao.dto.BindExpertAgentsCommand;
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
import com.databuff.digitalexpert.dao.dto.ImportExpertPackageResponse;
import com.databuff.digitalexpert.dao.dto.ManualCreateExpertResponse;
import com.databuff.digitalexpert.dao.dto.ManualUpdateExpertResponse;
import com.databuff.digitalexpert.dao.dto.McpBindingRequest;
import com.databuff.digitalexpert.dao.dto.SubmitExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.SubmitForwardTrainingRequest;
import com.databuff.digitalexpert.dao.dto.UpdateExpertBindingsCommand;
import com.databuff.digitalexpert.dao.dto.UpdateExpertCommand;
import com.databuff.digitalexpert.dao.dto.UpdateManualExpertRequest;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
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

    @PostMapping
    public ApiResponse<ExpertSummaryResponse> createExpert(@Valid @RequestBody CreateExpertRequest request) {
        return ApiResponse.success(digitalExpertService.createExpert(request));
    }

    @PostMapping("/list")
    public ApiResponse<List<ExpertSummaryResponse>> listExperts(@Valid @RequestBody ExpertBatchQueryRequest request) {
        return ApiResponse.success(digitalExpertService.listExpertsByNames(
                request.names(),
                request.expertType(),
                request.appNames(),
                request.status()
        ));
    }

    @PostMapping("/agent-bindings/list")
    public ApiResponse<List<AgentBindingGroupResponse>> listAgentBindings() {
        return ApiResponse.success(digitalExpertService.listAgentBindings());
    }

    @PostMapping("/agent-bindings/bind")
    public ApiResponse<ExpertBindingUpdateResponse> bindAgents(@Valid @RequestBody BindExpertAgentsCommand request) {
        return ApiResponse.success(digitalExpertService.bindAgents(request));
    }

    @PostMapping(value = "/manual/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ManualCreateExpertResponse> createManualExpert(@RequestParam("name") String name,
                                                                       @RequestParam(value = "description", required = false) String description,
                                                                       @RequestParam(value = "prompt", required = false) String prompt,
                                                                       @RequestParam(value = "expertType", required = false) String expertType,
                                                                       @RequestParam(value = "iconUrl", required = false) String iconUrl,
                                                                       @RequestParam(value = "mcpsJson", required = false) String mcpsJson,
                                                                       @RequestParam(value = "autoRelease", defaultValue = "true") boolean autoRelease,
                                                                       @RequestParam(value = "agentIdsJson", required = false) String agentIdsJson,
                                                                       @RequestPart("skillFiles") List<MultipartFile> skillFiles) {
        return ApiResponse.success(digitalExpertService.createManualExpert(
                new CreateManualExpertRequest(
                        name,
                        description,
                        prompt,
                        expertType,
                        iconUrl,
                        parseMcpsJson(mcpsJson),
                        autoRelease,
                        parseAgentIdsJson(agentIdsJson)
                ),
                skillFiles
        ));
    }

    @PostMapping(value = "/manual/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ManualUpdateExpertResponse> updateManualExpert(@RequestParam("expertId") Long expertId,
                                                                       @RequestParam("name") String name,
                                                                       @RequestParam(value = "description", required = false) String description,
                                                                       @RequestParam(value = "iconUrl", required = false) String iconUrl,
                                                                       @RequestParam(value = "prompt", required = false) String prompt,
                                                                       @RequestParam(value = "expertType", required = false) String expertType,
                                                                       @RequestParam(value = "mcpsJson", required = false) String mcpsJson,
                                                                       @RequestPart(value = "skillFiles", required = false) List<MultipartFile> skillFiles) {
        return ApiResponse.success(digitalExpertService.updateManualExpert(
                new UpdateManualExpertRequest(
                        expertId,
                        name,
                        description,
                        iconUrl,
                        prompt,
                        expertType,
                        parseNullableMcpsJson(mcpsJson)
                ),
                skillFiles
        ));
    }

    @PostMapping(value = "/training-tasks/forward/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ForwardTrainingSubmitResponse> submitForwardTraining(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(value = "expertType", required = false) String expertType,
            @RequestParam("sourceType") String sourceType,
            @RequestParam(value = "sourceValue", required = false) String sourceValue,
            @RequestParam(value = "sourceVersion", required = false) String sourceVersion,
            @RequestParam(value = "trainingGoal", required = false) String trainingGoal,
            @RequestParam MultiValueMap<String, String> requestParams,
            @RequestPart(value = "docPackageFile", required = false) MultipartFile docPackageFile) {
        if (docPackageFile != null && !docPackageFile.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "当前版本暂不支持文档压缩包正向训练");
        }
        return ApiResponse.success(digitalExpertService.submitForwardTraining(
                new SubmitForwardTrainingRequest(
                        name,
                        description,
                        prompt,
                        expertType,
                        sourceType,
                        sourceValue,
                        sourceVersion,
                        trainingGoal,
                        parseAttachmentIds(requestParams)
                )
        ));
    }

    @PostMapping("/bindings/update")
    public ApiResponse<ExpertBindingUpdateResponse> updateBindings(@Valid @RequestBody UpdateExpertBindingsCommand request) {
        return ApiResponse.success(
                digitalExpertService.updateBindings(request.expertId(), request.toBindingsRequest())
        );
    }

    @PostMapping("/update")
    public ApiResponse<ExpertSummaryResponse> updateExpert(@Valid @RequestBody UpdateExpertCommand request) {
        return ApiResponse.success(digitalExpertService.updateExpert(request));
    }

    @PostMapping("/delete")
    public ApiResponse<Boolean> deleteExpert(@Valid @RequestBody ExpertIdRequest request) {
        return ApiResponse.success(digitalExpertService.deleteExpert(request.expertId()));
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
        return ApiResponse.success(
                expertTrainingService.submitTrainingTask(request.expertId(), request.toTrainingTaskRequest())
        );
    }

    @PostMapping("/training-tasks/detail")
    public ApiResponse<ExpertTrainingTaskResponse> getTrainingTask(@Valid @RequestBody ExpertTaskRequest request) {
        return ApiResponse.success(expertTrainingService.getTask(request.expertId(), request.taskId()));
    }

    @PostMapping("/training-tasks/abort")
    public ApiResponse<Boolean> abortTrainingTask(@Valid @RequestBody ExpertTaskRequest request) {
        return ApiResponse.success(expertTrainingService.abortTask(request.expertId(), request.taskId()));
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

    @PostMapping(value = "/package/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportExpertPackageResponse> importPackage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "autoRelease", defaultValue = "true") boolean autoRelease) {
        return ApiResponse.success(digitalExpertService.importExpertPackage(file, autoRelease));
    }

    @PostMapping("/status/change")
    public ApiResponse<List<ExpertSummaryResponse>> changeStatus(@Valid @RequestBody ChangeExpertStatusRequest request) {
        return ApiResponse.success(digitalExpertService.changeExpertStatus(request.expertIds(), request.operation()));
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

    private List<McpBindingRequest> parseNullableMcpsJson(String mcpsJson) {
        if (mcpsJson == null) {
            return null;
        }
        return parseMcpsJson(mcpsJson);
    }

    private List<Long> parseAgentIdsJson(String agentIdsJson) {
        if (agentIdsJson == null || agentIdsJson.isBlank()) {
            return List.of();
        }
        try {
            List<Long> result = JSON.parseArray(agentIdsJson, Long.class);
            return result == null ? List.of() : result;
        } catch (Exception ex) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "智能体 ID JSON 格式不正确");
        }
    }

    private List<Long> parseAttachmentIds(MultiValueMap<String, String> requestParams) {
        if (requestParams == null || requestParams.isEmpty()) {
            return List.of();
        }
        List<String> rawValues = new ArrayList<>();
        addAll(rawValues, requestParams.get("attachmentIdsJson"));
        addAll(rawValues, requestParams.get("attachmentIds"));
        if (rawValues.isEmpty()) {
            return List.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            parseAttachmentIdValue(rawValue, result);
        }
        return List.copyOf(result);
    }

    private void addAll(List<String> target, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        target.addAll(values);
    }

    private void parseAttachmentIdValue(String rawValue, Set<Long> result) {
        String value = rawValue.trim();
        try {
            if (value.startsWith("[")) {
                List<Long> ids = JSON.parseArray(value, Long.class);
                if (ids != null) {
                    ids.stream()
                            .filter(id -> id != null)
                            .forEach(result::add);
                }
                return;
            }
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    result.add(Long.parseLong(item.trim()));
                }
            }
        } catch (Exception ex) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "附件资源 ID JSON 格式不正确");
        }
    }
}
