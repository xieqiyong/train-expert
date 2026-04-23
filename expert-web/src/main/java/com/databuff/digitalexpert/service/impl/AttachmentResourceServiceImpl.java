package com.databuff.digitalexpert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.AttachmentResourceResponse;
import com.databuff.digitalexpert.dao.dto.TrainingAttachmentRef;
import com.databuff.digitalexpert.dao.entity.AttachmentResourceEntity;
import com.databuff.digitalexpert.dao.enums.AttachmentResourceScope;
import com.databuff.digitalexpert.dao.enums.AttachmentResourceStatus;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import com.databuff.digitalexpert.dao.mapper.AttachmentResourceMapper;
import com.databuff.digitalexpert.service.AttachmentResourceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AttachmentResourceServiceImpl implements AttachmentResourceService {

    @Autowired
    private AttachmentResourceMapper attachmentResourceMapper;

    @Override
    public List<AttachmentResourceResponse> listResources(String scope, String status) {
        String normalizedScope = normalizeScope(scope, false);
        String normalizedStatus = normalizeStatus(status, false);
        LambdaQueryWrapper<AttachmentResourceEntity> queryWrapper = new LambdaQueryWrapper<AttachmentResourceEntity>()
                .orderByAsc(AttachmentResourceEntity::getSortNo, AttachmentResourceEntity::getId);
        if (StringUtils.hasText(normalizedScope)) {
            queryWrapper.eq(AttachmentResourceEntity::getScope, normalizedScope);
        }
        queryWrapper.eq(AttachmentResourceEntity::getStatus,
                StringUtils.hasText(normalizedStatus) ? normalizedStatus : AttachmentResourceStatus.ACTIVE.name());
        return attachmentResourceMapper.selectList(queryWrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<TrainingAttachmentRef> requireActiveByIds(List<Long> ids) {
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<AttachmentResourceEntity> entities = attachmentResourceMapper.selectList(
                new LambdaQueryWrapper<AttachmentResourceEntity>()
                        .in(AttachmentResourceEntity::getId, normalizedIds)
                        .eq(AttachmentResourceEntity::getStatus, AttachmentResourceStatus.ACTIVE.name())
        );
        Map<Long, AttachmentResourceEntity> entityMap = new LinkedHashMap<>();
        for (AttachmentResourceEntity entity : entities) {
            entityMap.put(entity.getId(), entity);
        }
        List<TrainingAttachmentRef> result = new ArrayList<>();
        for (Long id : normalizedIds) {
            AttachmentResourceEntity entity = entityMap.get(id);
            if (entity == null) {
                throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "附件资源不存在或已禁用: " + id);
            }
            result.add(toTrainingAttachment(entity));
        }
        return result;
    }

    @Override
    public List<TrainingAttachmentRef> listReverseDefaultAttachments() {
        return attachmentResourceMapper.selectList(
                        new LambdaQueryWrapper<AttachmentResourceEntity>()
                                .eq(AttachmentResourceEntity::getScope, AttachmentResourceScope.REVERSE_DEFAULT.name())
                                .eq(AttachmentResourceEntity::getStatus, AttachmentResourceStatus.ACTIVE.name())
                                .orderByAsc(AttachmentResourceEntity::getSortNo, AttachmentResourceEntity::getId)
                ).stream()
                .map(this::toTrainingAttachment)
                .toList();
    }

    private AttachmentResourceResponse toResponse(AttachmentResourceEntity entity) {
        return new AttachmentResourceResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getResourceType(),
                entity.getStoragePath(),
                entity.getAccessUrl(),
                entity.getUsagePrompt(),
                entity.getScope(),
                entity.getStatus(),
                entity.getSortNo()
        );
    }

    private TrainingAttachmentRef toTrainingAttachment(AttachmentResourceEntity entity) {
        String resourceType = normalizeResourceType(entity.getResourceType());
        String storagePath = normalizeRequiredText(entity.getStoragePath(), "附件资源存储路径不能为空: " + entity.getId());
        return new TrainingAttachmentRef(
                entity.getId(),
                normalizeRequiredText(entity.getName(), "附件资源名称不能为空: " + entity.getId()),
                normalizeOptionalText(entity.getDescription()),
                resourceType,
                storagePath,
                normalizeOptionalText(entity.getAccessUrl()),
                normalizeOptionalText(entity.getUsagePrompt())
        );
    }

    private String normalizeResourceType(String resourceType) {
        String normalized = normalizeRequiredText(resourceType, "附件资源类型不能为空").toUpperCase(Locale.ROOT);
        try {
            return TrainingSourceType.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "附件资源类型不支持: " + resourceType);
        }
    }

    private String normalizeScope(String scope, boolean required) {
        String normalized = normalizeOptionalText(scope);
        if (normalized == null) {
            if (required) {
                throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "附件资源作用域不能为空");
            }
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        try {
            return AttachmentResourceScope.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "附件资源作用域不支持: " + scope);
        }
    }

    private String normalizeStatus(String status, boolean required) {
        String normalized = normalizeOptionalText(status);
        if (normalized == null) {
            if (required) {
                throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "附件资源状态不能为空");
            }
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        try {
            return AttachmentResourceStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "附件资源状态不支持: " + status);
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> values = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                values.add(id);
            }
        }
        return List.copyOf(values);
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
