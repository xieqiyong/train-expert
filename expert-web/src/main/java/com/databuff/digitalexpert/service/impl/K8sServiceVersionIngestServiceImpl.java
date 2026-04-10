package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.databuff.digitalexpert.dao.entity.ServiceVersionSnapshotEntity;
import com.databuff.digitalexpert.service.K8sServiceVersionIngestService;
import com.databuff.digitalexpert.service.ServiceVersionSnapshotService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class K8sServiceVersionIngestServiceImpl implements K8sServiceVersionIngestService {

    private static final int SUPPORTED_MESSAGE_TYPE = 41;

    @Autowired
    private ServiceVersionSnapshotService serviceVersionSnapshotService;

    @Override
    public void ingest(String payload, String topic, int partition, long offset) {
        if (!StringUtils.hasText(payload)) {
            return;
        }
        if (!JSON.isValidObject(payload)) {
            return;
        }
        try {
            JSONObject root = JSON.parseObject(payload);
            if (!isSupportedMessageType(root)) {
                return;
            }
            List<ServiceVersionSnapshotEntity> snapshots = parseSnapshots(root, payload, topic, offset);
            if (snapshots.isEmpty()) {
                return;
            }
            for (ServiceVersionSnapshotEntity snapshot : snapshots) {
                serviceVersionSnapshotService.saveOrUpdate(snapshot);
            }
            log.info("Kafka 消息 serviceVersion 入库完成, topic={}, partition={}, offset={}, recordCount={}",
                    topic, partition, offset, snapshots.size());
        } catch (Exception ex) {
            log.error("Kafka 消息解析并入库失败, topic={}, partition={}, offset={}", topic, partition, offset, ex);
        }
    }

    private boolean isSupportedMessageType(JSONObject root) {
        if (root == null) {
            return false;
        }
        Integer type = root.getInteger("type");
        return type != null && type == SUPPORTED_MESSAGE_TYPE;
    }

    private List<ServiceVersionSnapshotEntity> parseSnapshots(JSONObject root,
                                                             String payload,
                                                             String topic,
                                                             long offset) {
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            return List.of();
        }
        JSONArray containerStatuses = data.getJSONArray("containerStatuses");
        if (containerStatuses == null || containerStatuses.isEmpty()) {
            return List.of();
        }
        JSONObject metadata = data.getJSONObject("metadata");
        String clusterId = normalizeRequiredValue(root.getString("clusterId"));
        String clusterName = normalizeOptionalValue(root.getString("clusterName"));
        String namespace = firstNonBlank(
                root.getString("namespace"),
                metadata == null ? null : metadata.getString("namespace")
        );
        String podName = firstNonBlank(
                root.getString("name"),
                metadata == null ? null : metadata.getString("name")
        );
        String workloadName = firstNonBlank(
                root.getString("wlName"),
                resolveLabelValue(metadata, "app"),
                extractTagValue(data.getJSONArray("tags"), "kube_service")
        );
        String status = firstNonBlank(root.getString("status"), data.getString("status"), data.getString("phase"));
        LocalDateTime lastSeenAt = resolveLastSeenAt(root);
        List<ServiceVersionSnapshotEntity> snapshots = new ArrayList<>();
        for (int i = 0; i < containerStatuses.size(); i++) {
            JSONObject containerStatus = containerStatuses.getJSONObject(i);
            if (containerStatus == null) {
                continue;
            }
            String containerName = normalizeRequiredValue(containerStatus.getString("name"));
            String image = normalizeOptionalValue(containerStatus.getString("image"));
            String tag = extractImageTag(image);
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String appName = firstNonBlank(workloadName, containerName);
            ServiceVersionSnapshotEntity snapshot = new ServiceVersionSnapshotEntity();
            snapshot.setAppName(appName);
            snapshot.setClusterId(clusterId);
            snapshot.setClusterName(clusterName);
            snapshot.setNamespace(namespace);
            snapshot.setWorkloadName(workloadName);
            snapshot.setPodName(podName);
            snapshot.setContainerName(containerName);
            snapshot.setImageName(image);
            snapshot.setServiceVersion(tag);
            snapshot.setStatus(status);
            snapshot.setSourceTopic(topic);
            snapshot.setMessageOffset(offset);
            snapshot.setRawPayload(payload);
            snapshot.setLastSeenAt(lastSeenAt);
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private LocalDateTime resolveLastSeenAt(JSONObject root) {
        Long dtsReceiveTime = root.getLong("dtsReceiveTime");
        if (dtsReceiveTime != null && dtsReceiveTime > 0) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(dtsReceiveTime), ZoneId.systemDefault());
        }
        Long timestamp = root.getLong("timestamp");
        if (timestamp != null && timestamp > 0) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
        }
        return LocalDateTime.now();
    }

    private String resolveLabelValue(JSONObject metadata, String labelName) {
        if (metadata == null || !StringUtils.hasText(labelName)) {
            return null;
        }
        return extractTagValue(metadata.getJSONArray("labels"), labelName);
    }

    private String extractTagValue(JSONArray values, String key) {
        if (values == null || values.isEmpty() || !StringUtils.hasText(key)) {
            return null;
        }
        for (int i = 0; i < values.size(); i++) {
            String value = values.getString(i);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String prefix = key + ":";
            if (value.startsWith(prefix) && value.length() > prefix.length()) {
                return value.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private String extractImageTag(String image) {
        if (!StringUtils.hasText(image)) {
            return null;
        }
        int slashIndex = image.lastIndexOf('/');
        int colonIndex = image.lastIndexOf(':');
        if (colonIndex <= slashIndex || colonIndex >= image.length() - 1) {
            return null;
        }
        return image.substring(colonIndex + 1).trim();
    }

    private String normalizeRequiredValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeOptionalValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String abbreviatePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return "";
        }
        String normalized = payload.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 200) {
            return normalized;
        }
        return normalized.substring(0, 200) + "...";
    }
}
