package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AppInfoUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface AppInfoUploadService {

    AppInfoUploadResult saveArchive(MultipartFile file, String fileType);

    AppInfoUploadResult extractArchive(AppInfoUploadResult uploadResult);
}
