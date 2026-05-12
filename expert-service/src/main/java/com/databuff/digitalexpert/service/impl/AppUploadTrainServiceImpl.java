package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.AppInfoUploadResult;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.service.AppInfoUploadService;
import com.databuff.digitalexpert.service.AppUploadTrainService;
import com.databuff.digitalexpert.service.AutoTrainingTriggerService;
import com.databuff.digitalexpert.service.support.AppUploadGuardService;
import com.databuff.digitalexpert.util.AppInfoUploadNamingUtils;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class AppUploadTrainServiceImpl implements AppUploadTrainService {

    @Autowired
    private AppInfoUploadService appInfoUploadService;
    @Autowired
    private AutoTrainingTriggerService autoTrainingTriggerService;
    @Autowired
    private AppUploadGuardService appUploadGuardService;
    @Autowired
    @Qualifier("appUploadExecutor")
    private Executor appUploadExecutor;

    @Override
    public boolean uploadAndTrain(MultipartFile file, String fileType) {
        String originalFileName = AppInfoUploadNamingUtils.resolveOriginalFileName(file);
        String appName = AppInfoUploadNamingUtils.resolveAppName(originalFileName);
        if (appUploadGuardService.hasActiveTrainingTask(appName)) {
            log.info("应用存在进行中的训练任务，本次上传请求已忽略, appName={}, fileName={}", appName, originalFileName);
            return true;
        }

        String lockToken = appUploadGuardService.tryAcquireUploadLock(appName);
        if (lockToken == null) {
            log.info("应用已有上传或触发训练链路正在处理中，本次上传请求已忽略, appName={}, fileName={}", appName, originalFileName);
            return true;
        }

        try {
            AppInfoUploadResult savedArchive = appInfoUploadService.saveArchive(file, fileType);
            appUploadExecutor.execute(() -> processUploadAndTrain(savedArchive, lockToken));
            return true;
        } catch (RejectedExecutionException ex) {
            appUploadGuardService.releaseUploadLock(appName, lockToken);
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "上传任务执行队列已满，请稍后重试");
        } catch (RuntimeException ex) {
            appUploadGuardService.releaseUploadLock(appName, lockToken);
            throw ex;
        }
    }

    private void processUploadAndTrain(AppInfoUploadResult savedArchive, String lockToken) {
        String appName = savedArchive.appName();
        try {
            AppInfoUploadResult uploadResult = appInfoUploadService.extractArchive(savedArchive);
            autoTrainingTriggerService.trigger(uploadResult);
            log.info("应用上传异步处理完成，已触发训练, appName={}, archivePath={}", appName, savedArchive.storagePath());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.ACTIVE_TRAINING_TASK_EXISTS) {
                log.info("应用存在进行中的训练任务，重复上传已忽略, appName={}, archivePath={}", appName, savedArchive.storagePath());
                return;
            }
            log.error("应用上传异步处理失败, appName={}, archivePath={}", appName, savedArchive.storagePath(), ex);
        } catch (Exception ex) {
            log.error("应用上传异步处理失败, appName={}, archivePath={}", appName, savedArchive.storagePath(), ex);
        } finally {
            appUploadGuardService.releaseUploadLock(appName, lockToken);
        }
    }
}
