package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.AppInfoUploadResult;
import com.databuff.digitalexpert.dao.dto.AutoTrainingTriggerResponse;
import com.databuff.digitalexpert.dao.dto.CreateExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import com.databuff.digitalexpert.service.AutoExpertResolveService;
import com.databuff.digitalexpert.service.AutoTrainingTriggerService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AutoTrainingTriggerServiceImpl implements AutoTrainingTriggerService {

    @Autowired
    private AutoExpertResolveService autoExpertResolveService;
    @Autowired
    private ExpertTrainingService expertTrainingService;

    @Override
    public AutoTrainingTriggerResponse trigger(AppInfoUploadResult uploadResult) {
        if (uploadResult == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "上传结果不能为空");
        }
        String appName = normalizeAppName(uploadResult.appName());
        Path appInfoPath = validateAppInfoDirectory(uploadResult.appInfoPath());

        AutoExpertResolveService.ResolvedExpert resolvedExpert =
                autoExpertResolveService.resolveOrCreateByServiceName(appName, uploadResult.serviceName());
        ExpertTrainingTaskResponse taskResponse = expertTrainingService.submitTrainingTask(
                resolvedExpert.expertId(),
                new CreateExpertTrainingTaskRequest(
                        List.of(new TrainingSourceRequest(TrainingSourceType.LOCAL_PATH.name(), appInfoPath.toString())),
                        buildTrainingGoal(appName, appInfoPath)
                )
        );
        return new AutoTrainingTriggerResponse(
                resolvedExpert.expertId(),
                resolvedExpert.expertName(),
                resolvedExpert.created(),
                taskResponse.taskId(),
                taskResponse.status()
        );
    }

    private String normalizeAppName(String appName) {
        if (!StringUtils.hasText(appName)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "应用名称不能为空");
        }
        return appName.trim();
    }

    private Path validateAppInfoDirectory(String appInfoPath) {
        if (!StringUtils.hasText(appInfoPath)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "app_info 路径不能为空");
        }
        Path directory = Path.of(appInfoPath).normalize();
        if (!Files.isDirectory(directory)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "app_info 目录不存在: " + directory);
        }
        Path appJsonPath = directory.resolve("app.json");
        if (!Files.isRegularFile(appJsonPath)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "app_info 目录缺少 app.json: " + directory);
        }
        Path jarsDirectory = directory.resolve("jars");
        if (!Files.isDirectory(jarsDirectory)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "app_info 目录缺少 jars: " + directory);
        }
        try (var stream = Files.list(jarsDirectory)) {
            boolean hasJar = stream.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName() != null
                    && path.getFileName().toString().toLowerCase().endsWith(".jar"));
            if (!hasJar) {
                throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "jars 目录下未找到 jar 文件: " + jarsDirectory);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "读取 jars 目录失败: " + jarsDirectory);
        }
        return directory;
    }

    private String buildTrainingGoal(String appName, Path appInfoPath) {
        return "基于 jars 目录生成 " + appName + " 的数字专家技能，jarsPath="
                + appInfoPath.resolve("jars").normalize();
    }
}
