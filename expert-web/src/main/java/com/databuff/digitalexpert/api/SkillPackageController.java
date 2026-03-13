package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.SkillPackageResponse;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.SkillPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/skills")
public class SkillPackageController {

    private final SkillPackageService skillPackageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillPackageResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(skillPackageService.upload(file));
    }

    @GetMapping("/{id}")
    public ApiResponse<SkillPackageResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(skillPackageService.getById(id));
    }
}
