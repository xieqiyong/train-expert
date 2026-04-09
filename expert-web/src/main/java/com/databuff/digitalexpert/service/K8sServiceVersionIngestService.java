package com.databuff.digitalexpert.service;

public interface K8sServiceVersionIngestService {

    void ingest(String payload, String topic, int partition, long offset);
}
