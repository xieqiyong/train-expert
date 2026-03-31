package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_agent_skill_deployment")
public class AgentSkillDeploymentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private Long skillId;
    @TableField("skill_directory_name")
    private String skillDirectoryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
