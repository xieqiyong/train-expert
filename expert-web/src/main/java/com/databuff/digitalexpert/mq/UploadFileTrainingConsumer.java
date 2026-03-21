package com.databuff.digitalexpert.mq;

import com.alibaba.fastjson2.JSON;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.config.ExpertProperties;
import com.databuff.digitalexpert.dao.bo.UploadFileKafkaMessage;
import com.databuff.digitalexpert.dao.dto.CreateExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import com.databuff.digitalexpert.service.AutoExpertResolveService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "digital-expert.training.kafka", name = "enabled", havingValue = "true")
public class UploadFileTrainingConsumer {

    @Autowired
    private ExpertTrainingService expertTrainingService;
    @Autowired
    private AutoExpertResolveService autoExpertResolveService;
    @Autowired
    private ExpertProperties properties;

    @KafkaListener(
            topics = "${digital-expert.training.kafka.topic}",
            groupId = "${digital-expert.training.kafka.group-id}"
    )
    public void consume(String payload) {
        if (!StringUtils.hasText(payload)) {
            log.warn("Kafka 自动训练消息为空，已跳过");
            return;
        }
        try {
            UploadFileKafkaMessage message = JSON.parseObject(payload, UploadFileKafkaMessage.class);
            if (message == null || !StringUtils.hasText(message.getStoragePath())) {
                log.warn("Kafka 自动训练消息缺少 storagePath，已跳过, payload={}", payload);
                return;
            }

            Path appInfoDirectory = resolveAppInfoDirectory(message);
            if (!isValidAppInfoDirectory(appInfoDirectory)) {
                log.warn("Kafka 自动训练的 app_info 目录无效，已跳过, storagePath={}, appInfoDir={}",
                        message.getStoragePath(), appInfoDirectory);
                return;
            }

            String appName = resolveAppName(appInfoDirectory, message);
            AutoExpertResolveService.ResolvedExpert resolvedExpert =
                    autoExpertResolveService.resolveOrCreateByServiceName(appName);
            CreateExpertTrainingTaskRequest request = new CreateExpertTrainingTaskRequest(
                    List.of(new TrainingSourceRequest(TrainingSourceType.LOCAL_PATH.name(), appInfoDirectory.toString())),
                    buildTrainingGoal(appName, appInfoDirectory)
            );

            ExpertTrainingTaskResponse response =
                    expertTrainingService.submitTrainingTask(resolvedExpert.expertId(), request);
            log.info("Kafka 自动训练任务已创建, expertId={}, expertName={}, created={}, taskId={}, appName={}, appInfoDir={}",
                    resolvedExpert.expertId(),
                    resolvedExpert.expertName(),
                    resolvedExpert.created(),
                    response.taskId(),
                    appName,
                    appInfoDirectory);
        } catch (BusinessException ex) {
            log.warn("Kafka 自动训练触发失败: {}", ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Kafka 自动训练消费失败, payload={}", payload, ex);
        }
    }

    private Path resolveAppInfoDirectory(UploadFileKafkaMessage message) {
        Path storagePath = Path.of(message.getStoragePath()).normalize();
        Path parent = storagePath.getParent();
        if (parent == null) {
            return storagePath.resolveSibling(properties.getTraining().getKafka().getAppInfoDirName()).normalize();
        }
        return parent.resolve(properties.getTraining().getKafka().getAppInfoDirName()).normalize();
    }

    private boolean isValidAppInfoDirectory(Path appInfoDirectory) {
        if (appInfoDirectory == null || !Files.isDirectory(appInfoDirectory)) {
            return false;
        }
        Path appJson = appInfoDirectory.resolve("app.json");
        Path jarsDirectory = appInfoDirectory.resolve("jars");
        if (!Files.isRegularFile(appJson) || !Files.isDirectory(jarsDirectory)) {
            return false;
        }
        try (var stream = Files.list(jarsDirectory)) {
            return stream.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName() != null
                    && path.getFileName().toString().toLowerCase().endsWith(".jar"));
        } catch (Exception ex) {
            log.warn("校验 app_info 目录失败, appInfoDir={}", appInfoDirectory, ex);
            return false;
        }
    }

    private String resolveAppName(Path appInfoDirectory, UploadFileKafkaMessage message) {
        Path timestampDirectory = appInfoDirectory.getParent();
        Path appNameDirectory = timestampDirectory == null ? null : timestampDirectory.getParent();
        if (appNameDirectory != null && appNameDirectory.getFileName() != null) {
            String appName = appNameDirectory.getFileName().toString();
            if (StringUtils.hasText(appName)) {
                return appName;
            }
        }
        if (StringUtils.hasText(message.getOriginalFileName()) && message.getOriginalFileName().contains("_app_info")) {
            return message.getOriginalFileName().substring(0, message.getOriginalFileName().indexOf("_app_info"));
        }
        return "unknown";
    }

    private String buildTrainingGoal(String appName, Path appInfoDirectory) {
        return "基于 jars 目录生成 " + appName + " 的数字专家技能"
                + "，jarsPath=" + appInfoDirectory.resolve("jars").normalize();
    }
}