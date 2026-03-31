package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_expert_training_task")
public class ExpertTrainingTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long expertId;
    private String status;
    private String previousExpertStatus;
    private String sessionId;
    private String submitRequestId;
    private String sourceManifestJson;
    private String outputDir;
    private String manifestPath;
    private String releaseTaskId;
    private Integer pollCount;
    private Integer artifactVerified;
    private String failureReason;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
