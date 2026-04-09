package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_service_version_snapshot")
public class ServiceVersionSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("app_name")
    private String appName;
    @TableField("cluster_id")
    private String clusterId;
    @TableField("cluster_name")
    private String clusterName;
    private String namespace;
    @TableField("workload_name")
    private String workloadName;
    @TableField("pod_name")
    private String podName;
    @TableField("container_name")
    private String containerName;
    @TableField("image_name")
    private String imageName;
    @TableField("service_version")
    private String serviceVersion;
    private String status;
    @TableField("source_topic")
    private String sourceTopic;
    @TableField("message_offset")
    private Long messageOffset;
    @TableField("raw_payload")
    private String rawPayload;
    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
