package com.databuff.digitalexpert.dao.dto;

public record AppInfoUploadAndTrainResponse(
        AppInfoUploadResult upload,
        AutoTrainingTriggerResponse training
) {
}
