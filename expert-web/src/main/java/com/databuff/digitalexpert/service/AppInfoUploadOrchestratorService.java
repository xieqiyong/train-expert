package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AppInfoUploadAndTrainResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AppInfoUploadOrchestratorService {

    AppInfoUploadAndTrainResponse uploadAndTrain(MultipartFile file, String fileType);
}
