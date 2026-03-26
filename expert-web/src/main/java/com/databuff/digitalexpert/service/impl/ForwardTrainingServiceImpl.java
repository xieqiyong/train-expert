package com.databuff.digitalexpert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.CreateExpertTrainingTaskRequest;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.dto.ExpertTrainingTaskResponse;
import com.databuff.digitalexpert.dao.dto.ForwardTrainingSubmitResponse;
import com.databuff.digitalexpert.dao.dto.SubmitForwardTrainingRequest;
import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertType;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.service.DigitalExpertService;
import com.databuff.digitalexpert.service.ExpertTrainingService;
import com.databuff.digitalexpert.service.ForwardTrainingService;
import com.databuff.digitalexpert.service.ForwardTrainingSourceStorageService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ForwardTrainingServiceImpl implements ForwardTrainingService {

    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private DigitalExpertService digitalExpertService;
    @Autowired
    private ExpertTrainingService expertTrainingService;
    @Autowired
    private ForwardTrainingSourceStorageService forwardTrainingSourceStorageService;

    @Override
    @Transactional
    public ForwardTrainingSubmitResponse submit(SubmitForwardTrainingRequest request, MultipartFile docPackageFile) {
        if (request == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "正向训练请求不能为空");
        }

        String expertName = normalizeRequiredText(request.name(), "训练名称不能为空");
        String sourceType = normalizeSourceType(request.sourceType());
        String sourceVersion = normalizeOptionalText(request.sourceVersion());

        ResolvedExpert resolvedExpert = resolveOrCreateExpert(request, expertName);
        TrainingSourceRequest source = buildTrainingSource(
                resolvedExpert.expert.name(),
                sourceType,
                request.sourceValue(),
                sourceVersion,
                docPackageFile
        );
        ExpertTrainingTaskResponse trainingTask = expertTrainingService.submitTrainingTask(
                resolvedExpert.expert.id(),
                new CreateExpertTrainingTaskRequest(
                        List.of(source),
                        normalizeOptionalText(request.trainingGoal())
                )
        );
        return new ForwardTrainingSubmitResponse(
                resolvedExpert.expert,
                resolvedExpert.created,
                trainingTask,
                source.sourceType(),
                source.sourceVersion()
        );
    }

    private ResolvedExpert resolveOrCreateExpert(SubmitForwardTrainingRequest request, String expertName) {
        DigitalExpertEntity existingExpert = findByName(expertName);
        if (existingExpert == null) {
            return createExpert(request, expertName);
        }
        updateExpert(existingExpert, request);
        return new ResolvedExpert(toSummary(existingExpert), false);
    }

    private ResolvedExpert createExpert(SubmitForwardTrainingRequest request, String expertName) {
        try {
            ExpertSummaryResponse expert = digitalExpertService.createExpert(new CreateExpertRequest(
                    expertName,
                    normalizeOptionalText(request.description()),
                    normalizeOptionalText(request.prompt()),
                    resolveCreateExpertType(request.expertType())
            ));
            return new ResolvedExpert(expert, true);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.DUPLICATE_RESOURCE) {
                throw ex;
            }
            DigitalExpertEntity concurrentExpert = findByName(expertName);
            if (concurrentExpert == null) {
                throw ex;
            }
            updateExpert(concurrentExpert, request);
            return new ResolvedExpert(toSummary(concurrentExpert), false);
        }
    }

    private void updateExpert(DigitalExpertEntity expert, SubmitForwardTrainingRequest request) {
        expert.setDescription(normalizeOptionalText(request.description()));
        expert.setPrompt(normalizeOptionalText(request.prompt()));
        expert.setExpertType(resolveUpdateExpertType(request.expertType(), expert.getExpertType()));
        expert.setUpdatedAt(LocalDateTime.now());
        digitalExpertMapper.updateById(expert);
    }

    private TrainingSourceRequest buildTrainingSource(String expertName,
                                                     String sourceType,
                                                     String sourceValue,
                                                     String sourceVersion,
                                                     MultipartFile docPackageFile) {
        if (TrainingSourceType.GIT_URL.name().equals(sourceType)) {
            String gitUrl = normalizeRequiredText(sourceValue, "Git 地址不能为空");
            String branch = normalizeRequiredText(sourceVersion, "Git 训练必须填写分支信息");
            return new TrainingSourceRequest(TrainingSourceType.GIT_URL.name(), gitUrl, branch);
        }
        if (TrainingSourceType.DOC_PACKAGE.name().equals(sourceType)) {
            ForwardTrainingSourceStorageService.StoredDocumentSource storedDocumentSource =
                    forwardTrainingSourceStorageService.storeDocumentPackage(expertName, docPackageFile);
            return new TrainingSourceRequest(
                    TrainingSourceType.DOC_PACKAGE.name(),
                    storedDocumentSource.extractedDirectoryPath(),
                    sourceVersion
            );
        }
        throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                "正向训练暂不支持的训练源类型: " + sourceType);
    }

    private DigitalExpertEntity findByName(String expertName) {
        return digitalExpertMapper.selectOne(new LambdaQueryWrapper<DigitalExpertEntity>()
                .eq(DigitalExpertEntity::getName, expertName)
                .last("limit 1"));
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = normalizeRequiredText(sourceType, "训练源类型不能为空").toUpperCase(Locale.ROOT);
        if (!TrainingSourceType.GIT_URL.name().equals(normalized)
                && !TrainingSourceType.DOC_PACKAGE.name().equals(normalized)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST,
                    "正向训练仅支持 GIT_URL 或 DOC_PACKAGE");
        }
        return normalized;
    }

    private String resolveCreateExpertType(String expertType) {
        String normalized = normalizeOptionalText(expertType);
        return normalizeExpertTypeValue(normalized, ExpertType.SERVICE.name());
    }

    private String resolveUpdateExpertType(String requestedExpertType, String currentExpertType) {
        String normalized = normalizeOptionalText(requestedExpertType);
        if (StringUtils.hasText(normalized)) {
            return normalizeExpertTypeValue(normalized, ExpertType.SERVICE.name());
        }
        if (StringUtils.hasText(currentExpertType)) {
            return currentExpertType;
        }
        return ExpertType.SERVICE.name();
    }

    private String normalizeRequiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeExpertTypeValue(String expertType, String defaultValue) {
        if (!StringUtils.hasText(expertType)) {
            return defaultValue;
        }
        String normalizedType = expertType.trim();
        if ("服务类型".equals(normalizedType)) {
            return ExpertType.SERVICE.name();
        }
        if ("内置类型".equals(normalizedType)) {
            return ExpertType.BUILTIN.name();
        }
        if ("故障分析".equals(normalizedType)) {
            return ExpertType.FAULT_ANALYSIS.name();
        }
        try {
            return ExpertType.valueOf(normalizedType.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(
                    ErrorCode.INVALID_REQUEST,
                    "专家类型不支持，当前仅支持：SERVICE、BUILTIN、FAULT_ANALYSIS"
            );
        }
    }

    private ExpertSummaryResponse toSummary(DigitalExpertEntity entity) {
        return new ExpertSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getExpertType(),
                entity.getStatus()
        );
    }

    private record ResolvedExpert(ExpertSummaryResponse expert, boolean created) {
    }
}
