package com.databuff.digitalexpert.facade.service;

import com.databuff.digitalexpert.dao.dto.FileUploadResponse;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface PublicFileFacadeService {

    FileUploadResponse uploadImage(MultipartFile file);

    PublicImageFile loadPublicImage(String date, String fileName);

    record PublicImageFile(
            Path path,
            String contentType,
            long contentLength
    ) {
    }
}
