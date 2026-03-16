package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.StaticPackageResponse;
import com.databuff.digitalexpert.dao.entity.StaticPackageEntity;
import org.springframework.web.multipart.MultipartFile;

public interface StaticPackageService {

    StaticPackageResponse upload(String name, String staticType, String description, MultipartFile file);

    StaticPackageResponse getById(Long id);

    StaticPackageEntity requireById(Long id);
}
