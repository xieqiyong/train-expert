package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.SkillPackageResponse;
import com.databuff.digitalexpert.dao.entity.SkillPackageEntity;
import org.springframework.web.multipart.MultipartFile;

public interface SkillPackageService {

    SkillPackageResponse upload(MultipartFile file);

    SkillPackageResponse getById(Long id);

    SkillPackageEntity requireById(Long id);
}
