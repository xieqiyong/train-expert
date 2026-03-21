package com.databuff.digitalexpert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.CreateExpertRequest;
import com.databuff.digitalexpert.dao.dto.ExpertSummaryResponse;
import com.databuff.digitalexpert.dao.entity.DigitalExpertEntity;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.mapper.DigitalExpertMapper;
import com.databuff.digitalexpert.service.AutoExpertResolveService;
import com.databuff.digitalexpert.service.DigitalExpertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AutoExpertResolveServiceImpl implements AutoExpertResolveService {

    private final DigitalExpertMapper digitalExpertMapper;
    private final DigitalExpertService digitalExpertService;

    @Override
    public ResolvedExpert resolveOrCreateByServiceName(String serviceName) {
        String normalizedServiceName = normalizeServiceName(serviceName);

        DigitalExpertEntity existingExpert = findByName(normalizedServiceName);
        if (existingExpert != null) {
            return new ResolvedExpert(existingExpert.getId(), existingExpert.getName(), false);
        }

        try {
            ExpertSummaryResponse expert = digitalExpertService.createExpert(
                    new CreateExpertRequest(normalizedServiceName, normalizedServiceName, null)
            );
            return new ResolvedExpert(expert.id(), expert.name(), true);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.DUPLICATE_RESOURCE) {
                throw ex;
            }
            DigitalExpertEntity concurrentCreatedExpert = findByName(normalizedServiceName);
            if (concurrentCreatedExpert != null) {
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
}
