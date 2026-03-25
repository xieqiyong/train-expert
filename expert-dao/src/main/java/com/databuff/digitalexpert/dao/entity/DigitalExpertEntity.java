package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("digital_expert")
public class DigitalExpertEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String prompt;
    private String expertType;
    private String status;
    private String sharedPath;
    private String configJsonPath;
    private String zipPackagePath;
    private String lastReleaseTaskId;
    private Integer releaseVersion;
    private String lastTrainingTaskId;
    private Integer trainingVersion;
    private String lastTrainingSessionId;
    private LocalDateTime startedAt;
    private LocalDateTime disabledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
