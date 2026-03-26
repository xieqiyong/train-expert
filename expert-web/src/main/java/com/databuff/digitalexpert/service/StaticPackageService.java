package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.StaticPackageResponse;
import com.databuff.digitalexpert.dao.entity.StaticPackageEntity;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface StaticPackageService {

    StaticPackageResponse upload(String name, String staticType, String description, MultipartFile file);

    StaticPackageResponse getById(Long id);

    List<StaticPackageResponse> listPackages();

    StaticPackageEntity requireById(Long id);
}
