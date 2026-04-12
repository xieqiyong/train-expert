package com.databuff.digitalexpert.service;

import org.springframework.web.multipart.MultipartFile;

public interface AppUploadTrainService {

    boolean uploadAndTrain(MultipartFile file, String fileType);
}
