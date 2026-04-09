package com.databuff.digitalexpert.service.impl;

import com.alibaba.fastjson2.JSON;
import com.databuff.digitalexpert.service.KafkaServiceVersionService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class KafkaServiceVersionServiceImpl implements KafkaServiceVersionService {

    @Override
    public void handleDcDatabuffK8sMessage(String payload, String topic, int partition, long offset) {
        Optional<String> serviceVersion = extractServiceVersion(payload);
        if (serviceVersion.isPresent()) {
            log.info("Kafka 消息解析 serviceVersion 成功, topic={}, partition={}, offset={}, serviceVersion={}",
                    topic, partition, offset, serviceVersion.get());
            return;
        }
        log.warn("Kafka 消息未解析到 serviceVersion, topic={}, partition={}, offset={}, payloadPreview={}",
                topic, partition, offset, abbreviatePayload(payload));
    }

    @Override
    public Optional<String> extractServiceVersion(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }
        // 具体消息结构待样例明确后，再补充 serviceVersion 提取规则。
        return Optional.empty();
    }

    @Override
    public Optional<String> findLatestServiceVersionByAppName(String appName) {
        return Optional.empty();
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
