package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("expert_release_task")
public class ExpertReleaseTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long expertId;
    private String status;
    private String triggerType;
    private String activeTaskKey;
    private String stagingPath;
    private String configJsonPath;
    private String zipPackagePath;
    private String failureReason;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

