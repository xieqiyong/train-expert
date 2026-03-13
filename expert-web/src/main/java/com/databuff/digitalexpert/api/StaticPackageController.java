package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.StaticPackageResponse;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.StaticPackageService;
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
@RequestMapping("/api/v1/static-packages")
public class StaticPackageController {

    private final StaticPackageService staticPackageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StaticPackageResponse> upload(@RequestParam String name,
                                                     @RequestParam(required = false) String description,
                                                     @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(staticPackageService.upload(name, description, file));
    }

    @GetMapping("/{id}")
    public ApiResponse<StaticPackageResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(staticPackageService.getById(id));
    }
}
