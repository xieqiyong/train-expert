package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.ForwardTrainingSubmitResponse;
import com.databuff.digitalexpert.dao.dto.SubmitForwardTrainingRequest;
import org.springframework.web.multipart.MultipartFile;

public interface ForwardTrainingService {

    ForwardTrainingSubmitResponse submit(SubmitForwardTrainingRequest request, MultipartFile docPackageFile);
}

