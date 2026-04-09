package com.databuff.digitalexpert.service;

import java.util.Optional;

public interface KafkaServiceVersionService {

    void handleDcDatabuffK8sMessage(String payload, String topic, int partition, long offset);

    Optional<String> extractServiceVersion(String payload);

    Optional<String> findLatestServiceVersionByAppName(String appName);
}
