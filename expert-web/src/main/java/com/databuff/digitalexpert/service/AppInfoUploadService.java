package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AppInfoUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface AppInfoUploadService {

    AppInfoUploadResult upload(MultipartFile file, String fileType);
}
