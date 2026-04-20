package com.databuff.digitalexpert.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.ExpertSource;
import com.databuff.digitalexpert.dao.enums.ExpertType;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.service.AutoExpertResolveService;
import com.databuff.digitalexpert.service.DigitalExpertService;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AutoExpertResolveServiceImpl implements AutoExpertResolveService {

    @Autowired
    private DigitalExpertMapper digitalExpertMapper;
    @Autowired
    private DigitalExpertService digitalExpertService;

    @Override
    public ResolvedExpert resolveOrCreateByServiceName(String serviceName, String rawServiceName) {
        String normalizedServiceName = normalizeServiceName(serviceName);
        String originalServiceName = normalizeRawServiceName(rawServiceName);

        DigitalExpertEntity existingExpert = findByName(normalizedServiceName);
        if (existingExpert != null) {
            syncAutoExpertFields(
                    existingExpert.getId(),
                    existingExpert.getAppName(),
                    existingExpert.getAliasName(),
                    originalServiceName,
                    normalizedServiceName,
                    false
            );
            return new ResolvedExpert(existingExpert.getId(), existingExpert.getName(), false);
        }

        try {
            ExpertSummaryResponse expert = digitalExpertService.createExpert(
                    new CreateExpertRequest(normalizedServiceName, normalizedServiceName, null, ExpertType.SERVICE.name(), null),
                    ExpertSource.GENERATED
            );
            syncAutoExpertFields(expert.id(), null, expert.aliasName(), originalServiceName, normalizedServiceName, true);
            return new ResolvedExpert(expert.id(), expert.name(), true);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.DUPLICATE_RESOURCE) {
                throw ex;
            }
            DigitalExpertEntity concurrentCreatedExpert = findByName(normalizedServiceName);
            if (concurrentCreatedExpert != null) {
                syncAutoExpertFields(
                        concurrentCreatedExpert.getId(),
                        concurrentCreatedExpert.getAppName(),
                        concurrentCreatedExpert.getAliasName(),
                        originalServiceName,
                        normalizedServiceName,
                        false
                );
                return new ResolvedExpert(concurrentCreatedExpert.getId(), concurrentCreatedExpert.getName(), false);
            }
            throw ex;
        }
    }

    private DigitalExpertEntity findByName(String expertName) {
        return digitalExpertMapper.selectOne(new LambdaQueryWrapper<DigitalExpertEntity>()
                .eq(DigitalExpertEntity::getName, expertName)
                .last("limit 1"));
    }

    private String normalizeServiceName(String serviceName) {
        if (serviceName == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "服务名称不能为空");
        }
        String normalized = serviceName.trim();
        if (!StringUtils.hasText(normalized)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "服务名称不能为空");
        }
        return normalized;
    }

    private String normalizeRawServiceName(String rawServiceName) {
        return StringUtils.hasText(rawServiceName) ? rawServiceName : null;
    }

    private void syncAutoExpertFields(
            Long expertId,
            String currentAppName,
            String currentAliasName,
            String rawServiceName,
            String normalizedServiceName,
            boolean forceGeneratedSource
    ) {
        if (expertId == null) {
            return;
        }
        boolean shouldSyncAppName = rawServiceName != null && !Objects.equals(currentAppName, rawServiceName);
        boolean shouldSyncAliasName = !StringUtils.hasText(currentAliasName)
                && StringUtils.hasText(normalizedServiceName);
        boolean shouldSyncGeneratedSource = forceGeneratedSource;
        if (!shouldSyncAppName && !shouldSyncAliasName && !shouldSyncGeneratedSource) {
            return;
        }

        LambdaUpdateWrapper<DigitalExpertEntity> updateWrapper = new LambdaUpdateWrapper<DigitalExpertEntity>()
                .eq(DigitalExpertEntity::getId, expertId)
                .set(DigitalExpertEntity::getUpdatedAt, LocalDateTime.now());
        if (shouldSyncAppName) {
            updateWrapper.set(DigitalExpertEntity::getAppName, rawServiceName);
        }
        if (shouldSyncAliasName) {
            updateWrapper.set(DigitalExpertEntity::getAliasName, normalizedServiceName);
        }
        if (shouldSyncGeneratedSource) {
            updateWrapper.set(DigitalExpertEntity::getExpertSource, ExpertSource.GENERATED.name());
        }
        digitalExpertMapper.update(null, updateWrapper);
    }
}
