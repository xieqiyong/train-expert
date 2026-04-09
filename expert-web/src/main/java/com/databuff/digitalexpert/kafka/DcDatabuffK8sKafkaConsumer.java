package com.databuff.digitalexpert.kafka;

import com.databuff.digitalexpert.service.K8sServiceVersionIngestService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "digital-expert.kafka", name = "enabled", havingValue = "true")
public class DcDatabuffK8sKafkaConsumer {

    @Autowired
    private K8sServiceVersionIngestService k8sServiceVersionIngestService;

    @KafkaListener(
            topics = "${digital-expert.kafka.topics.dc-databuff-k8s}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        if (record == null) {
            return;
        }
        log.info("开始消费 Kafka 消息, topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());
        k8sServiceVersionIngestService.ingest(
                record.value(),
                record.topic(),
                record.partition(),
                record.offset()
        );
    }
}
