package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_digital_expert")
public class DigitalExpertEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField("alias_name")
    private String aliasName;
    @TableField("app_name")
    private String appName;
    private String description;
    private String prompt;
    private String expertType;
    @TableField("expert_source")
    private String expertSource;
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
