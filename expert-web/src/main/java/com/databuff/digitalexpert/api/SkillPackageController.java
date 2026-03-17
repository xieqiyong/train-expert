package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.SkillPackageResponse;
import com.databuff.digitalexpert.dao.dto.IdRequest;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.SkillPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/skills")
public class SkillPackageController {

    @Autowired
    private SkillPackageService skillPackageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillPackageResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(skillPackageService.upload(file));
    }

    @PostMapping("/detail")
    public ApiResponse<SkillPackageResponse> getById(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(skillPackageService.getById(request.id()));
    }
}
