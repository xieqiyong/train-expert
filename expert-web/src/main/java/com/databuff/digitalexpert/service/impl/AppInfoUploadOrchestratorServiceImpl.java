package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.dao.dto.AppInfoUploadAndTrainResponse;
import com.databuff.digitalexpert.dao.dto.AppInfoUploadResult;
import com.databuff.digitalexpert.dao.dto.AutoTrainingTriggerResponse;
import com.databuff.digitalexpert.service.AppInfoUploadOrchestratorService;
import com.databuff.digitalexpert.service.AppInfoUploadService;
import com.databuff.digitalexpert.service.AutoTrainingTriggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AppInfoUploadOrchestratorServiceImpl implements AppInfoUploadOrchestratorService {

    @Autowired
    private AppInfoUploadService appInfoUploadService;
    @Autowired
    private AutoTrainingTriggerService autoTrainingTriggerService;

    @Override
    public AppInfoUploadAndTrainResponse uploadAndTrain(MultipartFile file, String fileType) {
        AppInfoUploadResult uploadResult = appInfoUploadService.upload(file, fileType);
        AutoTrainingTriggerResponse trainingResponse = autoTrainingTriggerService.trigger(uploadResult);
        return new AppInfoUploadAndTrainResponse(uploadResult, trainingResponse);
    }
}
