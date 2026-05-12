package com.databuff.digitalexpert.facade.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.FileUploadResponse;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.facade.common.FacadeBusinessException;
import com.databuff.digitalexpert.facade.service.PublicFileFacadeService;
import com.databuff.digitalexpert.service.PublicFileService;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PublicFileFacadeServiceImpl implements PublicFileFacadeService {

    @Autowired
    private PublicFileService publicFileService;

    @Override
    public FileUploadResponse uploadImage(MultipartFile file) {
        return new FileUploadResponse(publicFileService.uploadImage(file));
    }

    @Override
    public PublicImageFile loadPublicImage(String date, String fileName) {
        PublicFileService.PublicImageResource resource = publicFileService.loadImage(date, fileName);
        try {
            return new PublicImageFile(
                    resource.path(),
                    resource.mediaType().toString(),
                    Files.size(resource.path())
            );
        } catch (IOException ex) {
            throw FacadeBusinessException.internal(ErrorCode.INTERNAL_ERROR, "读取图片文件失败");
        } catch (BusinessException ex) {
            throw ex;
        }
    }
}
