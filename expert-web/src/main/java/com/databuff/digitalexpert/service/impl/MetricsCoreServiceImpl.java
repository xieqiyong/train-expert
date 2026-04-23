package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.MetricsCoreCategoryResponse;
import com.databuff.digitalexpert.dao.dto.MetricsCoreQueryRequest;
import com.databuff.digitalexpert.dao.dto.MetricsCoreResponse;
import com.databuff.digitalexpert.dao.dto.MetricsResourceFileResponse;
import com.databuff.digitalexpert.dao.dto.MetricsResourceGenerateRequest;
import com.databuff.digitalexpert.dao.entity.AttachmentResourceEntity;
import com.databuff.digitalexpert.dao.entity.MetricsCoreEntity;
import com.databuff.digitalexpert.dao.enums.AttachmentResourceScope;
import com.databuff.digitalexpert.dao.enums.AttachmentResourceStatus;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.dao.enums.TrainingSourceType;
import com.databuff.digitalexpert.dao.mapper.AttachmentResourceMapper;
import com.databuff.digitalexpert.dao.mapper.MetricsCoreMapper;
import com.databuff.digitalexpert.service.MetricsCoreService;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class MetricsCoreServiceImpl implements MetricsCoreService {

    private static final int DEFAULT_QUERY_LIMIT = 1000;
    private static final int MAX_QUERY_LIMIT = 10000;
    private static final String RESOURCE_DIRECTORY = "metric_resources";
    private static final String APPLICATION_PERFORMANCE_TYPE1 = "\u5e94\u7528\u6027\u80fd";

    @Autowired
    private MetricsCoreMapper metricsCoreMapper;
    @Autowired
    private AttachmentResourceMapper attachmentResourceMapper;
    @Autowired
    private SharedStorageService sharedStorageService;

    @Override
    public List<MetricsCoreResponse> list(MetricsCoreQueryRequest request) {
        return selectMetrics(request, resolveLimit(request == null ? null : request.limit())).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<MetricsCoreCategoryResponse> listCategories(MetricsCoreQueryRequest request) {
        List<MetricsCoreEntity> metrics = selectMetrics(request, null);
        Map<String, Map<String, Map<String, Integer>>> groups = new LinkedHashMap<>();
        for (MetricsCoreEntity metric : metrics) {
            groups.computeIfAbsent(resolveGroupName(metric.getType1()), key -> new LinkedHashMap<>())
                    .computeIfAbsent(resolveGroupName(metric.getType2()), key -> new LinkedHashMap<>())
                    .merge(resolveGroupName(metric.getType3()), 1, Integer::sum);
        }
        List<MetricsCoreCategoryResponse> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Integer>>> type1Entry : groups.entrySet()) {
            List<MetricsCoreCategoryResponse.Type2Group> type2Groups = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> type2Entry : type1Entry.getValue().entrySet()) {
                List<MetricsCoreCategoryResponse.Type3Group> type3Groups = type2Entry.getValue().entrySet().stream()
                        .map(entry -> new MetricsCoreCategoryResponse.Type3Group(entry.getKey(), entry.getValue()))
                        .toList();
                type2Groups.add(new MetricsCoreCategoryResponse.Type2Group(type2Entry.getKey(), type3Groups));
            }
            result.add(new MetricsCoreCategoryResponse(type1Entry.getKey(), type2Groups));
        }
        return result;
    }

    @Override
    @Transactional
    public List<MetricsResourceFileResponse> generateResourceFiles(MetricsResourceGenerateRequest request) {
        MetricsCoreQueryRequest queryRequest = new MetricsCoreQueryRequest(
                normalizeTexts(request == null ? null : request.type1s()),
                null,
                null,
                null,
                request == null ? null : request.app(),
                request == null ? null : request.database(),
                null,
                request == null ? null : request.includeDisabled(),
                null
        );
        List<MetricsCoreEntity> metrics = selectMetrics(queryRequest, null);
        Map<String, List<MetricsCoreEntity>> groupMap = new LinkedHashMap<>();
        for (MetricsCoreEntity metric : metrics) {
            groupMap.computeIfAbsent(resolveResourceGroupName(metric), key -> new ArrayList<>()).add(metric);
        }
        List<MetricsResourceFileResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<MetricsCoreEntity>> entry : groupMap.entrySet()) {
            Map<String, String> items = buildResourceItems(entry.getValue());
            if (items.isEmpty()) {
                continue;
            }
            Path resourcePath = writeResourceFile(entry.getKey(), items);
            AttachmentResourceEntity attachment = saveOrUpdateAttachmentResource(entry.getKey(), resourcePath);
            result.add(new MetricsResourceFileResponse(
                    entry.getKey(),
                    resourcePath.getFileName().toString(),
                    sharedStorageService.toStoragePath(resourcePath),
                    attachment.getId(),
                    items.size()
            ));
        }
        return result;
    }

    private List<MetricsCoreEntity> selectMetrics(MetricsCoreQueryRequest request, Integer limit) {
        MetricsCoreQueryRequest normalizedRequest = request == null
                ? new MetricsCoreQueryRequest(null, null, null, null, null, null, null, false, null)
                : request;
        LambdaQueryWrapper<MetricsCoreEntity> queryWrapper = new LambdaQueryWrapper<MetricsCoreEntity>()
                .orderByAsc(
                        MetricsCoreEntity::getType1,
                        MetricsCoreEntity::getType2,
                        MetricsCoreEntity::getType3,
                        MetricsCoreEntity::getMeasurement,
                        MetricsCoreEntity::getId
                );
        if (!Boolean.TRUE.equals(normalizedRequest.includeDisabled())) {
            queryWrapper.eq(MetricsCoreEntity::getIsOpen, 1);
        }
        List<String> type1s = normalizeTexts(normalizedRequest.type1s());
        String type1 = normalizeOptionalText(normalizedRequest.type1());
        if (!type1s.isEmpty()) {
            queryWrapper.in(MetricsCoreEntity::getType1, type1s);
        } else if (type1 != null) {
            queryWrapper.eq(MetricsCoreEntity::getType1, type1);
        }
        eqIfPresent(queryWrapper, MetricsCoreEntity::getType2, normalizedRequest.type2());
        eqIfPresent(queryWrapper, MetricsCoreEntity::getType3, normalizedRequest.type3());
        eqIfPresent(queryWrapper, MetricsCoreEntity::getApp, normalizedRequest.app());
        eqIfPresent(queryWrapper, MetricsCoreEntity::getDatabaseName, normalizedRequest.database());
        eqIfPresent(queryWrapper, MetricsCoreEntity::getMeasurement, normalizedRequest.measurement());
        if (limit != null) {
            queryWrapper.last("limit " + limit);
        }
        return metricsCoreMapper.selectList(queryWrapper);
    }

    private void eqIfPresent(LambdaQueryWrapper<MetricsCoreEntity> queryWrapper,
                             com.baomidou.mybatisplus.core.toolkit.support.SFunction<MetricsCoreEntity, ?> column,
                             String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null) {
            queryWrapper.eq(column, normalized);
        }
    }

    private Map<String, String> buildResourceItems(List<MetricsCoreEntity> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return Map.of();
        }
        Map<String, String> items = new LinkedHashMap<>();
        for (MetricsCoreEntity metric : metrics.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MetricsCoreEntity::getId))
                .toList()) {
            JSONObject fields = parseFields(metric);
            if (fields == null || fields.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Object> fieldEntry : fields.entrySet()) {
                String fieldKey = normalizeOptionalText(fieldEntry.getKey());
                if (fieldKey == null) {
                    continue;
                }
                String fieldDescription = resolveFieldDescription(fieldEntry.getValue());
                items.put(buildMetricFieldKey(metric, fieldKey), buildMetricFieldValue(metric, fieldDescription));
            }
        }
        return items;
    }

    private JSONObject parseFields(MetricsCoreEntity metric) {
        if (metric == null || !StringUtils.hasText(metric.getFields())) {
            return null;
        }
        try {
            return JSON.parseObject(metric.getFields());
        } catch (Exception ex) {
            log.warn("指标 fields 解析失败，跳过该指标, metricId={}, measurement={}",
                    metric.getId(), metric.getMeasurement(), ex);
            return null;
        }
    }

    private String buildMetricFieldKey(MetricsCoreEntity metric, String fieldKey) {
        String measurement = firstNonBlank(metric.getMeasurement(), "unknown_measurement");
        return measurement + "." + fieldKey;
    }

    private String buildMetricFieldValue(MetricsCoreEntity metric, String fieldDescription) {
        String metricDescription = firstNonBlank(metric.getDescText(), "\u672a\u547d\u540d\u6307\u6807");
        String normalizedFieldDescription = firstNonBlank(fieldDescription, "\u672a\u547d\u540d\u6307\u6807\u5b57\u6bb5");
        return "\u6307\u6807\u540d\u63cf\u8ff0\uff1a" + metricDescription
                + ",field\u63cf\u8ff0\uff1a" + normalizedFieldDescription;
    }

    private String resolveFieldDescription(Object value) {
        if (value instanceof JSONObject object) {
            return firstNonBlank(object.getString("describe"), object.getString("metric_cn"), "未命名指标字段");
        }
        if (value instanceof Map<?, ?> map) {
            Object describe = map.get("describe");
            if (describe != null && StringUtils.hasText(String.valueOf(describe))) {
                return String.valueOf(describe).trim();
            }
        }
        return "未命名指标字段";
    }

    private Path writeResourceFile(String type1, Map<String, String> items) {
        String fileName = "metrics-" + sanitizeFileName(type1) + ".json";
        Path resourcePath = sharedStorageService.getStaticPackage()
                .resolve(RESOURCE_DIRECTORY)
                .resolve(fileName)
                .normalize();
        String json = JSON.toJSONString(items, JSONWriter.Feature.PrettyFormat);
        sharedStorageService.writeBytes(resourcePath, json.getBytes(StandardCharsets.UTF_8));
        return resourcePath;
    }

    private AttachmentResourceEntity saveOrUpdateAttachmentResource(String type1, Path resourcePath) {
        String storagePath = sharedStorageService.toStoragePath(resourcePath);
        String name = "指标资源-" + type1;
        AttachmentResourceEntity existing = attachmentResourceMapper.selectOne(
                new LambdaQueryWrapper<AttachmentResourceEntity>()
                        .eq(AttachmentResourceEntity::getStoragePath, storagePath)
                        .last("limit 1")
        );
        LocalDateTime now = LocalDateTime.now();
        AttachmentResourceEntity entity = existing == null ? new AttachmentResourceEntity() : existing;
        entity.setName(name);
        entity.setDescription("指标体系资源: " + type1);
        entity.setResourceType(TrainingSourceType.LOCAL_PATH.name());
        entity.setStoragePath(storagePath);
        entity.setAccessUrl(null);
        entity.setUsagePrompt("请结合该指标体系资源理解监控指标、字段含义和中文描述。");
        entity.setScope(AttachmentResourceScope.GENERAL.name());
        entity.setStatus(AttachmentResourceStatus.ACTIVE.name());
        entity.setSortNo(0);
        entity.setUpdatedAt(now);
        if (existing == null) {
            entity.setCreatedAt(now);
            attachmentResourceMapper.insert(entity);
            return entity;
        }
        attachmentResourceMapper.updateById(entity);
        return entity;
    }

    private MetricsCoreResponse toResponse(MetricsCoreEntity entity) {
        return new MetricsCoreResponse(
                entity.getId(),
                entity.getType1(),
                entity.getType2(),
                entity.getType3(),
                entity.getApp(),
                entity.getDatabaseName(),
                entity.getMeasurement(),
                entity.getDescText(),
                entity.getTagKey(),
                entity.getTagValue(),
                entity.getFields(),
                entity.getIsOpen(),
                entity.getMetricType(),
                entity.getMetricSource(),
                entity.getBuiltin()
        );
    }

    private Integer resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_QUERY_LIMIT;
        }
        if (limit <= 0) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "查询数量必须大于 0");
        }
        return Math.min(limit, MAX_QUERY_LIMIT);
    }

    private String resolveGroupName(String value) {
        return firstNonBlank(value, "未分类");
    }

    private String resolveResourceGroupName(MetricsCoreEntity metric) {
        if (metric == null) {
            return resolveGroupName(null);
        }
        String type1 = normalizeOptionalText(metric.getType1());
        if (APPLICATION_PERFORMANCE_TYPE1.equals(type1)) {
            return resolveGroupName(type1);
        }
        return resolveGroupName(metric.getType2());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeOptionalText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private List<String> normalizeTexts(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeOptionalText(value);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sanitizeFileName(String value) {
        String normalized = firstNonBlank(value, "unknown");
        String sanitized = normalized
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");
        if (!StringUtils.hasText(sanitized)) {
            return "unknown";
        }
        return sanitized.toLowerCase(Locale.ROOT);
    }
}
