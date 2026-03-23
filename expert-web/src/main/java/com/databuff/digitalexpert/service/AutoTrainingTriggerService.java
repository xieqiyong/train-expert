package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.AppInfoUploadResult;
import com.databuff.digitalexpert.dao.dto.AutoTrainingTriggerResponse;

public interface AutoTrainingTriggerService {

    AutoTrainingTriggerResponse trigger(AppInfoUploadResult uploadResult);
}
