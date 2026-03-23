package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.AppInfoUploadAndTrainResponse;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.AppInfoUploadOrchestratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/file")
public class InternalAppInfoController {

    @Autowired
    private AppInfoUploadOrchestratorService appInfoUploadOrchestratorService;

    @PostMapping(value = "/uploadAndTrain", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AppInfoUploadAndTrainResponse> uploadAndTrain(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", required = false, defaultValue = "jar") String fileType) {
        return ApiResponse.success(appInfoUploadOrchestratorService.uploadAndTrain(file, fileType));
    }
}
