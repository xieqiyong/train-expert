package com.databuff.digitalexpert.service;

import org.springframework.web.multipart.MultipartFile;

public interface ForwardTrainingSourceStorageService {

    StoredDocumentSource storeDocumentPackage(String expertName, MultipartFile file);

    record StoredDocumentSource(
            String originalFileName,
            String archivePath,
            String extractedDirectoryPath
    ) {
    }
}
