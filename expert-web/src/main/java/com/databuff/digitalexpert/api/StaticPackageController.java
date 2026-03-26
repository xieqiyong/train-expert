package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.IdRequest;
import com.databuff.digitalexpert.dao.dto.StaticPackageResponse;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.StaticPackageService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/static-packages")
public class StaticPackageController {

    @Autowired
    private StaticPackageService staticPackageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StaticPackageResponse> upload(@RequestPart("name") String name,
                                                     @RequestPart("staticType") String staticType,
                                                     @RequestPart(value = "description", required = false) String description,
                                                     @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(staticPackageService.upload(name, staticType, description, file));
    }

    @PostMapping("/detail")
    public ApiResponse<StaticPackageResponse> getById(@Valid @RequestBody IdRequest request) {
        return ApiResponse.success(staticPackageService.getById(request.id()));
    }

    @PostMapping("/list")
    public ApiResponse<List<StaticPackageResponse>> listPackages() {
        return ApiResponse.success(staticPackageService.listPackages());
    }
}
